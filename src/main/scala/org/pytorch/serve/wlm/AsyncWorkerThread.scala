package org.pytorch.serve.wlm

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import org.pytorch.serve.job.{Job, RestJob}
import org.pytorch.serve.util.codec.ModelResponseDecoder
import org.pytorch.serve.util.messages.*
import org.pytorch.serve.util.{ConfigManager, Connector}
import org.pytorch.serve.wlm.WorkerThread.{ENCODER, logger, loggerTelemetryMetrics}
import org.slf4j.{Logger, LoggerFactory}

import java.io.IOException
import java.net.{HttpURLConnection, SocketAddress}
import java.util.UUID
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.jdk.CollectionConverters.*
object AsyncWorkerThread {
  // protected ConcurrentHashMap requestsInBackend;
  protected val logger: Logger = LoggerFactory.getLogger(classOf[AsyncWorkerThread])
  protected val WORKER_TIMEOUT = 2L
}

class AsyncWorkerThread(configManager: ConfigManager, backendEventGroup: EventLoopGroup, port: Int, gpuId: Int, model: Model, aggregator: BatchAggregator, listener: WorkerStateListener) extends WorkerThread(configManager, backendEventGroup, port, gpuId, model, aggregator, listener) {

  protected var loadingFinished = false
  protected var latch: CountDownLatch = null

  override def run(): Unit = {
    responseTimeout = model.getResponseTimeout
    startupTimeout = model.getStartupTimeout
    val thread = Thread.currentThread
    thread.setName(getWorkerName)
    currentThread.set(thread)
    var req :BaseModelRequest= null
    var status = HttpURLConnection.HTTP_INTERNAL_ERROR
    try {
      connect()
      while (isRunning) {
        req = aggregator.getRequest(workerId, state)
        val workerCmd = req.getCommand
        val wtStartTime = System.currentTimeMillis
        val repeats = getRepeats(workerCmd)
        AsyncWorkerThread.logger.debug("Flushing req.cmd {} repeats {} to backend at: {}", workerCmd, repeats, wtStartTime)
        try {
          backendChannel(0).writeAndFlush(req).sync
          AsyncWorkerThread.logger.debug("Successfully flushed req")
          if (!loadingFinished) {
            latch = new CountDownLatch(1)
            if (!latch.await(startupTimeout, TimeUnit.SECONDS)) throw new WorkerInitializationException("Worker did not load the model within " + startupTimeout + " seconds")
          }
        } catch {
          case e: InterruptedException =>
            AsyncWorkerThread.logger.error("Failed to send request to backend", e)
        }
        req = null
      }
    } catch {
      case e: InterruptedException =>
        AsyncWorkerThread.logger.debug("System state is : " + state)
        if ((state eq WorkerState.WORKER_SCALED_DOWN) || (state eq WorkerState.WORKER_STOPPED)) AsyncWorkerThread.logger.debug("Shutting down the thread .. Scaling down.")
        else AsyncWorkerThread.logger.debug("Backend worker monitoring thread interrupted or backend worker process died. responseTimeout:" + responseTimeout + "sec", e)
      case e: WorkerInitializationException =>
        AsyncWorkerThread.logger.error("Backend worker error", e)
      case oom: OutOfMemoryError =>
        AsyncWorkerThread.logger.error("Out of memory error when creating workers", oom)
        status = HttpURLConnection.HTTP_ENTITY_TOO_LARGE
        if (ConfigManager.getInstance.isTelemetryEnabled) {
          WorkerThread.loggerTelemetryMetrics.info("ModelServerError.Count:1|#TorchServe:{},{}:-1", ConfigManager.getInstance.getVersion, oom.getClass.getCanonicalName)
        }
      case e: IllegalStateException =>
        AsyncWorkerThread.logger.error("IllegalStateException error", e)
      case t: Throwable =>
        AsyncWorkerThread.logger.warn("Backend worker thread exception.", t)
        if (ConfigManager.getInstance.isTelemetryEnabled) {
          WorkerThread.loggerTelemetryMetrics.info("ModelServerError.Count:1|#TorchServe:{},{}:-1", ConfigManager.getInstance.getVersion, t.getClass.getCanonicalName)
        }
    } finally {

      // WorkerThread is running in thread pool, the thread will be assigned to next
      // Runnable once this worker is finished. If currentThread keep holding the reference
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

  @throws[WorkerInitializationException]
  @throws[InterruptedException]
  override protected def connect(): Unit = {
    if (!configManager.isDebug) {
      val ids = getDeviceIds
      AsyncWorkerThread.logger.debug("Device Ids: " + ids)
      lifeCycle.startWorker(port, ids)
    }
    val modelName = model.getModelName
    val modelVersion = model.getVersion
    setState(WorkerState.WORKER_STARTED, HttpURLConnection.HTTP_OK)
    val latch = new CountDownLatch(1)
    val responseBufferSize = configManager.getMaxResponseSize
    try {
      val connector = new Connector(port)
      val b = new Bootstrap
      b.group(backendEventGroup).channel(connector.getClientChannel).handler(new ChannelInitializer[Channel]() {
        override def initChannel(ch: Channel): Unit = {
          val p = ch.pipeline
          p.addLast(ENCODER)
          p.addLast(new ModelResponseDecoder(responseBufferSize))
          p.addLast(new AsyncWorkerHandler) // todo inner class bug
        }
      })
      val address = connector.getSocketAddress
      AsyncWorkerThread.logger.info("Connecting to: {}", address)
      backendChannel.append(b.connect(address).sync.channel)
      backendChannel(0).closeFuture.addListener((future: ChannelFuture) => {
        latch.countDown()
        AsyncWorkerThread.logger.info("{} Worker disconnected. {}", getWorkerId, state)
        val thread = currentThread.getAndSet(null)
        if (thread != null) thread.interrupt()
      }.asInstanceOf[ChannelFutureListener])
      backendChannel(0).newSucceededFuture.addListener((future: ChannelFuture) => {

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
      if (!latch.await(AsyncWorkerThread.WORKER_TIMEOUT, TimeUnit.MINUTES)) throw new WorkerInitializationException("Worker failed to initialize within " + AsyncWorkerThread.WORKER_TIMEOUT + " mins")
      running.set(true)
    } catch {
      case t: Throwable =>

        /* https://github.com/netty/netty/issues/2597 */
        if (t.isInstanceOf[IOException]) throw new WorkerInitializationException("Failed to connect to worker.", t)
        throw t
    }
  }

  @ChannelHandler.Sharable protected class AsyncWorkerHandler extends SimpleChannelInboundHandler[ModelWorkerResponse] {
    override def channelRead0(ctx: ChannelHandlerContext, msg: ModelWorkerResponse): Unit = {
      try {
        aggregator.sendResponse(msg)
        if (!loadingFinished) if (msg.getCode == 200) {
          AsyncWorkerThread.logger.info("Worker loaded the model successfully")
          setState(WorkerState.WORKER_MODEL_LOADED, HttpURLConnection.HTTP_OK)
          backoffIdx = 0
          loadingFinished = true
          latch.countDown()
        }
        else setState(WorkerState.WORKER_ERROR, msg.getCode)
      } catch {
        case e: NullPointerException =>
          AsyncWorkerThread.logger.error("Failed to send response", e)
          throw new IllegalStateException("Message was empty")
      }
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
      AsyncWorkerThread.logger.error("Unknown exception", cause)
      if (cause.isInstanceOf[OutOfMemoryError]) {
        val msg = new ModelWorkerResponse
        msg.setCode(HttpURLConnection.HTTP_ENTITY_TOO_LARGE)
        msg.setMessage(cause.getMessage)
        if (!replies.offer(msg)) throw new IllegalStateException("Reply queue is full.")
      }
      ctx.close
    }
  }
}