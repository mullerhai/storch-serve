package org.pytorch.serve.wlm

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import org.pytorch.serve.device.Accelerator
import org.pytorch.serve.job.{Job, RestJob}
import org.pytorch.serve.metrics.{IMetric, MetricCache}
import org.pytorch.serve.util.codec.{ModelRequestEncoder, ModelResponseDecoder}
import org.pytorch.serve.util.messages.*
import org.pytorch.serve.util.messages.WorkerCommands.*
import org.pytorch.serve.util.{ConfigManager, Connector}
import org.pytorch.serve.wlm.{ModelManager, WorkerInitializationException, WorkerLifeCycle, WorkerState}
import org.slf4j.{Logger, LoggerFactory}

import java.io.IOException
import java.net.{HttpURLConnection, SocketAddress}
import java.util
import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{ArrayBlockingQueue, CompletableFuture, CountDownLatch, TimeUnit}
import java.util.stream.Collectors
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
import scala.util.control.Breaks.{break, breakable}


object WorkerThread {
  protected val logger: Logger = LoggerFactory.getLogger(classOf[WorkerThread])
  val loggerTelemetryMetrics: Logger = LoggerFactory.getLogger(ConfigManager.MODEL_SERVER_TELEMETRY_LOGGER)
  protected val BACK_OFF: Array[Int] = Array(0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597)
  protected val WORKER_TIMEOUT = 2L
  val ENCODER = new ModelRequestEncoder(ConfigManager.getInstance.getPreferDirectBuffer)
}

class WorkerThread(protected var configManager: ConfigManager, protected var backendEventGroup: EventLoopGroup, protected var port: Int, protected var gpuId: Int, protected var model: Model, protected var aggregator: BatchAggregator, protected var listener: WorkerStateListener) extends Runnable {
//  this.workerId = String.valueOf(port) // Unique across all workers.
//  startTime = System.currentTimeMillis
////  lifeCycle = new WorkerLifeCycle(configManager, model)
//  replies = new ArrayBlockingQueue[ModelWorkerResponse](if (model.getParallelLevel > 0) model.getParallelLevel
//  else 1)
//  this.workerThreadTimeMetric = MetricCache.getInstance.getMetricFrontend("WorkerThreadTime")
//  this.workerLoadTimeMetric = MetricCache.getInstance.getMetricFrontend("WorkerLoadTime")
//  this.workerThreadTimeMetricDimensionValues = util.Arrays.asList("Host", ConfigManager.getInstance.getHostName)
//  this.workerLoadTimeMetricDimensionValues = util.Arrays.asList(getWorkerName, "Host", ConfigManager.getInstance.getHostName)
  final protected var workerThreadTimeMetric: IMetric = MetricCache.getInstance.getMetricFrontend("WorkerThreadTime")
  final protected var workerLoadTimeMetric: IMetric = MetricCache.getInstance.getMetricFrontend("WorkerLoadTime")
  final protected var workerThreadTimeMetricDimensionValues: List[String] = util.Arrays.asList("Host", ConfigManager.getInstance.getHostName).asScala.toList
  final protected var workerLoadTimeMetricDimensionValues: List[String] = util.Arrays.asList(getWorkerName, "Host", ConfigManager.getInstance.getHostName).asScala.toList
  protected var backendChannel = new ListBuffer[Channel]
  protected var running = new AtomicBoolean(true)
  protected var backoffIdx = 0
  protected var replies: ArrayBlockingQueue[ModelWorkerResponse] = new ArrayBlockingQueue[ModelWorkerResponse](if (model.getParallelLevel > 0) model.getParallelLevel
  else 1)
  protected var memory = 0L
  protected var startTime = System.currentTimeMillis
  protected var currentThread = new AtomicReference[Thread]
  protected var workerId: String = String.valueOf(port)
  protected var state: WorkerState = null
  protected var lifeCycle: WorkerLifeCycle =  new WorkerLifeCycle(configManager, model)
  protected var responseTimeout = 0
  protected var startupTimeout = 0
  protected var recoveryStartTS = 0L // 0: default value. no recovery needed, in healthy mode
  protected var req: BaseModelRequest = null

  def getState: WorkerState = state

  def getGpuUsage: String = {
    val gpuUsage = new StringBuffer
    if (gpuId >= 0) try {
      configManager.systemInfo.updateAcceleratorMetrics()
      val accelerator = this.configManager.systemInfo.getAccelerators(gpuId)
      return accelerator.utilizationToString
    } catch {
      case e: Exception =>
        gpuUsage.append("failed to obtained gpu usage")
        WorkerThread.logger.error("Exception Raised : " + e.toString)
    }
    else gpuUsage.append("N/A")
    gpuUsage.toString
  }

  def getLifeCycle: WorkerLifeCycle = lifeCycle

  override def run(): Unit = {
    responseTimeout = model.getResponseTimeout
    startupTimeout = model.getStartupTimeout
    val thread = Thread.currentThread
    thread.setName(getWorkerName)
    currentThread.set(thread)
    req = null
    var status = HttpURLConnection.HTTP_INTERNAL_ERROR
    try {
      connect()
      while (isRunning) {
        req = aggregator.getRequest(workerId, state)
        val workerCmd = req.getCommand
        // depending on type of worker command we determine which timeout we should use
        val timeout = if (workerCmd eq WorkerCommands.LOAD) startupTimeout
        else responseTimeout
        val wtStartTime = System.currentTimeMillis
        val repeats = getRepeats(workerCmd)
        WorkerThread.logger.debug("Flushing req.cmd {} repeats {} to backend at: {}", workerCmd, repeats, wtStartTime)
        val futureRequests = new ListBuffer[CompletableFuture[Void]] //(repeats)

        var i = 0
        while (backendChannel.size > 0 && i < repeats) {
          val idx = i
          futureRequests.append(CompletableFuture.runAsync(() => {
            try backendChannel(idx).writeAndFlush(req).sync
            catch {
              case e: InterruptedException =>
                WorkerThread.logger.error("Failed to send request to backend", e)
            }
          }))
          i += 1
        }
        futureRequests.map(cf => cf.join()) //CompletableFuture.join()
        var reply: ModelWorkerResponse = null
        var jobDone = false
        var totalDuration:Long = 0
        WorkerThread.logger.info("Looping backend response at: {}", System.currentTimeMillis)
        var condition = true
        while(condition){
          val begin = System.currentTimeMillis
          breakable(
            for (i <- 0 until repeats) {
              reply = replies.poll(timeout, TimeUnit.SECONDS)
              if (req.getCommand ne WorkerCommands.LOAD) break() //todo: break is not supported
            }
          )

          val duration = System.currentTimeMillis - begin
          if (reply != null) jobDone = aggregator.sendResponse(reply)
          else if (req.getCommand ne WorkerCommands.DESCRIBE) {
            val reqs = model.incrFailedInfReqs
            WorkerThread.logger.error("Number or consecutive unsuccessful inference {}", reqs )
            throw new WorkerInitializationException("Backend worker did not respond in given time")
          }
          totalDuration += duration
          condition = !jobDone
        }
//        do {
//          val begin = System.currentTimeMillis
//          for (i <- 0 until repeats) {
//            reply = replies.poll(timeout, TimeUnit.SECONDS)
//            if (req.getCommand ne WorkerCommands.LOAD) break //todo: break is not supported
//          }
//          val duration = System.currentTimeMillis - begin
//          if (reply != null) jobDone = aggregator.sendResponse(reply)
//          else if (req.getCommand ne WorkerCommands.DESCRIBE) {
//            val `val` = model.incrFailedInfReqs
//            WorkerThread.logger.error("Number or consecutive unsuccessful inference {}", `val`)
//            throw new WorkerInitializationException("Backend worker did not respond in given time")
//          }
//          totalDuration += duration
//        } while (!jobDone)
        WorkerThread.logger.info("Backend response time: {}", totalDuration)
        req.getCommand match {
          case PREDICT =>
          case STREAMPREDICT =>
          case STREAMPREDICT2 =>
            model.resetFailedInfReqs()
          case LOAD =>
            if (reply.getCode == 200) {
              setState(WorkerState.WORKER_MODEL_LOADED, HttpURLConnection.HTTP_OK)
              backoffIdx = 0
            }
            else {
              setState(WorkerState.WORKER_ERROR, reply.getCode)
              status = reply.getCode
            }
          case DESCRIBE =>
            if (reply == null) aggregator.sendError(req, "Failed to get customized model matadata.", HttpURLConnection.HTTP_INTERNAL_ERROR)
          case UNLOAD =>
          case STATS =>
          case _ =>
        }
        req = null
        val workerThreadTime = (System.currentTimeMillis - wtStartTime) - totalDuration
        if (this.workerThreadTimeMetric != null) try this.workerThreadTimeMetric.addOrUpdate(this.workerThreadTimeMetricDimensionValues.toList, workerThreadTime)
        catch {
          case e: Exception =>
            WorkerThread.logger.error("Failed to update frontend metric WorkerThreadTime: ", e)
        }
      }
    } catch {
      case e: InterruptedException =>
        WorkerThread.logger.debug("System state is : " + state)
        if ((state eq WorkerState.WORKER_SCALED_DOWN) || (state eq WorkerState.WORKER_STOPPED)) WorkerThread.logger.debug("Shutting down the thread .. Scaling down.")
        else if (state eq WorkerState.WORKER_STARTED) WorkerThread.logger.debug("Backend worker monitoring thread interrupted or backend worker process died., startupTimeout:" + startupTimeout + "sec", e)
        else WorkerThread.logger.debug("Backend worker monitoring thread interrupted or backend worker process died., responseTimeout:" + responseTimeout + "sec", e)
      case e: WorkerInitializationException =>
        WorkerThread.logger.error("Backend worker error", e)
      case oom: OutOfMemoryError =>
        WorkerThread.logger.error("Out of memory error when creating workers", oom)
        status = HttpURLConnection.HTTP_ENTITY_TOO_LARGE
        if (ConfigManager.getInstance.isTelemetryEnabled) WorkerThread.loggerTelemetryMetrics.info("ModelServerError.Count:1|#TorchServe:{},{}:-1", ConfigManager.getInstance.getVersion, oom.getClass.getCanonicalName)
      case e: IllegalStateException =>
        WorkerThread.logger.error("IllegalStateException error", e)
      case t: Throwable =>
        WorkerThread.logger.warn("Backend worker thread exception.", t)
        if (ConfigManager.getInstance.isTelemetryEnabled) WorkerThread.loggerTelemetryMetrics.info("ModelServerError.Count:1|#TorchServe:{},{}:-1", ConfigManager.getInstance.getVersion, t.getClass.getCanonicalName)
    } finally {

      // WorkerThread is running in thread pool, the thread will be assigned to next
      // Runnable once this worker is finished. If currentThread keep holding the
      // reference
      // of the thread, currentThread.interrupt() might kill next worker.
      for (i <- 0 until backendChannel.size) {
        backendChannel(i).disconnect
      }
      backendChannel.clear()
      currentThread.set(null)
      val exitValue = lifeCycle.getExitValue
      if (exitValue != null && (exitValue eq 137)) status = HttpURLConnection.HTTP_ENTITY_TOO_LARGE
      if (req != null) aggregator.sendError(req, "Worker died.", status)
      aggregator.cleanJobs()
      setState(WorkerState.WORKER_STOPPED, status)
      lifeCycle.exit()
      if (isHealthy) { // still within maxRetryTimeoutInMill window
        retry()
      }
    }
  }

  def getWorkerId: String = workerId

  def getMemory: Long = memory

  def setMemory(memory: Long): Unit = {
    this.memory = memory
  }

  def isRunning: Boolean = running.get

  @throws[WorkerInitializationException]
  @throws[InterruptedException]
  protected def connect(): Unit = {
    if (!configManager.isDebug) lifeCycle.startWorker(port, getDeviceIds)
    val modelName = model.getModelName
    val modelVersion = model.getVersion
    setState(WorkerState.WORKER_STARTED, HttpURLConnection.HTTP_OK)
    val parallelLevel = if (model.getParallelLevel > 0) model.getParallelLevel
    else 1
    val latch = new CountDownLatch(parallelLevel)
    val responseBufferSize = configManager.getMaxResponseSize
    try {
      for (i <- 0 until parallelLevel) {
        val connector = new Connector(port + i)
        val b = new Bootstrap
        b.group(backendEventGroup).channel(connector.getClientChannel).handler(new ChannelInitializer[Channel]() {
          override def initChannel(ch: Channel): Unit = {
            val p = ch.pipeline
            p.addLast(WorkerThread.ENCODER)
            p.addLast(new ModelResponseDecoder(responseBufferSize))
            p.addLast(new WorkerHandler) // todo inner class bug
          }
        })
        val address = connector.getSocketAddress
        WorkerThread.logger.info("Connecting to: {}", address)
        backendChannel.append(b.connect(address).sync.channel)
        backendChannel(i).closeFuture.addListener((future: ChannelFuture) => {
          latch.countDown()
          WorkerThread.logger.info("{} Worker disconnected. {}", getWorkerId, state)
          val thread = currentThread.getAndSet(null)
          if (thread != null) thread.interrupt()
        }.asInstanceOf[ChannelFutureListener])
        backendChannel(i).newSucceededFuture.addListener((future: ChannelFuture) => {

          // TODO:
          // use gpu, batch size in load model command
          if (latch.getCount == 1) {
            val input = new RequestInput(UUID.randomUUID.toString)
            if (gpuId >= 0) input.addParameter(new InputParameter("gpu", String.valueOf(gpuId)))
            val job = new RestJob(null, modelName, modelVersion, WorkerCommands.LOAD, input)
            model.addJob(workerId, job)
          }
          latch.countDown()
        }.asInstanceOf[ChannelFutureListener])
      }
      if (!latch.await(WorkerThread.WORKER_TIMEOUT, TimeUnit.MINUTES)) throw new WorkerInitializationException("Worker failed to initialize within " + WorkerThread.WORKER_TIMEOUT + " mins")
      running.set(true)
    } catch {
      case t: Throwable =>

        /* https://github.com/netty/netty/issues/2597 */
        if (t.isInstanceOf[IOException]) throw new WorkerInitializationException("Failed to connect to worker.", t)
        throw t
    }
  }

  protected def getDeviceIds: String = {
    var deviceIds: ListBuffer[Int] = new ListBuffer[Int]()
    if (gpuId == -1 || model.getParallelLevel == 0) null
    else if (model.isHasCfgDeviceIds) model.getDeviceIds.slice(gpuId, gpuId + model.getParallelLevel).mkString(",") //.stream.map(String.valueOf).collect(Collectors.joining(","))
    //    else if (model.isHasCfgDeviceIds) model.getDeviceIds.subList(gpuId, gpuId + model.getParallelLevel).stream.map(String.valueOf).collect(Collectors.joining(","))
    else {

      //      deviceIds = new util.ArrayList[Integer](model.getParallelLevel)
      for (i <- gpuId until gpuId + model.getParallelLevel) {
        deviceIds.append(i)
      }
      deviceIds.mkString(",")
      //      deviceIds.map(String.valueOf).collect(Collectors.joining(","))
    }
  }

  def shutdown(): Unit = {
    running.set(false)
    aggregator.shutdown()
    setState(WorkerState.WORKER_SCALED_DOWN, HttpURLConnection.HTTP_OK)
    for (i <- 0 until backendChannel.size) {
      if (backendChannel(i) != null) backendChannel(i).close
    }
    backendChannel.clear()
    val thread = currentThread.getAndSet(null)
    if (thread != null) {
      thread.interrupt()
      aggregator.sendError(null, "Worker scaled down.", HttpURLConnection.HTTP_INTERNAL_ERROR)
      model.removeJobQueue(workerId)
    }
  }

  def setState(newState: WorkerState, status: Int): Unit = {
    listener.notifyChangeState(model.getModelVersionName.getVersionedModelName, newState, status)
    WorkerThread.logger.debug("{} State change {} -> {}", getWorkerName, state, newState)
    val currentTS = System.currentTimeMillis
    val timeTaken = currentTS - startTime
    if (state ne WorkerState.WORKER_SCALED_DOWN) {
      // Don't update the state if it was terminated on purpose.. Scaling in..
      this.state = newState
    }
    if (state eq WorkerState.WORKER_MODEL_LOADED) {
      if (this.workerLoadTimeMetric != null) try this.workerLoadTimeMetric.addOrUpdate(this.workerLoadTimeMetricDimensionValues.toList, timeTaken)
      catch {
        case e: Exception =>
          WorkerThread.logger.error("Failed to update frontend metric WorkerLoadTime: ", e)
      }
      if (recoveryStartTS > 0) {
        WorkerThread.logger.info("Auto recovery succeeded, reset recoveryStartTS")
        recoveryStartTS = 0
      }
    }
    else if (state eq WorkerState.WORKER_STOPPED) if (recoveryStartTS == 0) {
      recoveryStartTS = currentTS
      WorkerThread.logger.info("Auto recovery start timestamp: {}", recoveryStartTS)
    }
    else WorkerThread.logger.warn("Auto recovery failed again")
  }

  protected def getWorkerName: String = {
    val modelName = model.getModelVersionName.getVersionedModelName
    "W-" + port + '-' + modelName
  }

  def getGpuId: Int = gpuId

  def getStartTime: Long = startTime

  def retry(): Unit = {
    if (state eq WorkerState.WORKER_SCALED_DOWN) {
      WorkerThread.logger.debug("Worker terminated due to scale-down call.")
      return
    }
    val manager = ModelManager.getInstance
    if (backoffIdx < WorkerThread.BACK_OFF.length - 1) backoffIdx += 1
    aggregator.startEventDispatcher()
    val r:Runnable = () => manager.submitTask(this)
    manager.getScheduler.schedule(r , WorkerThread.BACK_OFF(backoffIdx), TimeUnit.SECONDS)
    WorkerThread.logger.info("Retry worker: {} in {} seconds.", workerId, WorkerThread.BACK_OFF(backoffIdx))
  }

  def getPid: Int = lifeCycle.getPid

  def isHealthy: Boolean = {
    if (recoveryStartTS == 0 || (System.currentTimeMillis - recoveryStartTS) < model.getMaxRetryTimeoutInMill) return true
    false
  }

  @ChannelHandler.Sharable 
  protected class WorkerHandler extends SimpleChannelInboundHandler[ModelWorkerResponse] {
    override def channelRead0(ctx: ChannelHandlerContext, msg: ModelWorkerResponse): Unit = {
      try replies.offer(msg, responseTimeout, TimeUnit.SECONDS)
      catch {
        case e@(_: InterruptedException | _: NullPointerException) =>
          WorkerThread.logger.error("Failed to offer reply, responseTimeout:" + responseTimeout + "sec", e)
          throw new IllegalStateException("Reply queue is full.")
      }
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
      WorkerThread.logger.error("Unknown exception", cause)
      if (cause.isInstanceOf[OutOfMemoryError]) {
        val msg = new ModelWorkerResponse
        msg.setCode(HttpURLConnection.HTTP_ENTITY_TOO_LARGE)
        msg.setMessage(cause.getMessage)
        if (!replies.offer(msg)) throw new IllegalStateException("Reply queue is full.")
      }
      ctx.close
    }
  }

  protected def isTensorParallelRequest(workerCmd: WorkerCommands): Boolean = workerCmd match {
    case PREDICT =>   if (model.hasTensorParallel)  true else false
    case STREAMPREDICT =>   if (model.hasTensorParallel)  true else false
    case STREAMPREDICT2 =>
      if (model.hasTensorParallel) return true
      false
    case _ =>
      false
  }

  protected def isLoadRequest(workerCmd: WorkerCommands): Boolean = workerCmd eq WorkerCommands.LOAD

  protected def getRepeats(workerCmd: WorkerCommands): Int = {
    if (isLoadRequest(workerCmd) || isTensorParallelRequest(workerCmd)) {
      // broadcast the command to all ranks
      return Math.max(1, model.getParallelLevel)
    }
    1
  }
}