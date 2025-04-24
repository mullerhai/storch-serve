package org.pytorch.serve.archive.model

import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.NoSuchElementException
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks
import scala.util.control.Breaks.{break, breakable}

object ModelConfig {
  private val logger = LoggerFactory.getLogger(classOf[ModelConfig])
  val defaultStartupTimeout = 120 // unit: sec
  val defaultResponseTimeout = 120 // unit: sec

  def build(yamlMap: Map[String, AnyRef]): ModelConfig = {
    val modelConfig = new ModelConfig
    breakable(
      yamlMap.foreach((k: String, v: AnyRef) => {
        k match {
          case "minWorkers" =>
            if (v.isInstanceOf[Integer]) modelConfig.setMinWorkers(v.asInstanceOf[Int])
            else logger.warn("Invalid minWorkers: {}, should be integer", v)
          case "maxWorkers" =>
            if (v.isInstanceOf[Integer]) modelConfig.setMaxWorkers(v.asInstanceOf[Int])
            else logger.warn("Invalid maxWorkers: {}, should be integer", v)
          case "batchSize" =>
            if (v.isInstanceOf[Integer]) modelConfig.setBatchSize(v.asInstanceOf[Int])
            else logger.warn("Invalid batchSize: {}, should be integer", v)
          case "maxBatchDelay" =>
            if (v.isInstanceOf[Integer]) modelConfig.setMaxBatchDelay(v.asInstanceOf[Int])
            else logger.warn("Invalid maxBatchDelay: {}, should be integer", v)
          case "responseTimeout" =>
            if (v.isInstanceOf[Integer]) modelConfig.setResponseTimeout(v.asInstanceOf[Int])
            else logger.warn("Invalid responseTimeout: {}, should be integer", v)
          case "startupTimeout" =>
            if (v.isInstanceOf[Integer]) modelConfig.setStartupTimeout(v.asInstanceOf[Int])
            else logger.warn("Invalid startupTimeout: {}, should be integer", v)
          case "deviceType" =>
            if (v.isInstanceOf[String]) modelConfig.setDeviceType(v.asInstanceOf[String])
            else logger.warn("Invalid deviceType: {}, should be cpu, or gpu", v)
          case "parallelType" =>
            if (v.isInstanceOf[String]) modelConfig.setParallelMode(v.asInstanceOf[String])
            else logger.warn("Invalid parallelType: {}, should be pp, tp,or pptp", v)
          case "parallelLevel" =>
            if (v.isInstanceOf[Integer]) modelConfig.setParallelLevel(v.asInstanceOf[Int])
            else logger.warn("Invalid parallelLevel: {}, should be integer", v)
          case "deviceIds" =>
            if (v.isInstanceOf[List[?]]) modelConfig.setDeviceIds(v.asInstanceOf[List[?]])
            else logger.warn("Invalid deviceIds: {}, should be list of integer", v)
          case "torchrun" =>
            if (v.isInstanceOf[Map[?, ?]]) {
              modelConfig.torchRun = TorchRun.build(v.asInstanceOf[Map[?, ?]])
              modelConfig.setParallelLevel(modelConfig.torchRun.getNprocPerNode)
            }
            else logger.warn("Invalid torchrun: {}, should be Torchrun parameters", v)
          case "maxRetryTimeoutInSec" =>
            if (v.isInstanceOf[Integer]) modelConfig.setMaxRetryTimeoutInSec(v.asInstanceOf[Int])
            else logger.warn("Invalid maxRetryTimeoutInMin: {}, should be integer", v)
          case "clientTimeoutInMills" =>
            if (v.isInstanceOf[Integer]) modelConfig.setClientTimeoutInMills(v.asInstanceOf[Integer].longValue)
            else logger.warn("Invalid clientTimeoutInMills: {}, should be positive long", v)
          case "jobQueueSize" =>
            if (v.isInstanceOf[Integer]) modelConfig.setJobQueueSize(v.asInstanceOf[Int])
            else logger.warn("Invalid jobQueueSize: {}, should be positive int", v)
          case "useJobTicket" =>
            if (v.isInstanceOf[Boolean]) modelConfig.setUseJobTicket(v.asInstanceOf[Boolean])
            else logger.warn("Invalid useJobTicket: {}, should be true or false", v)
          case "sequenceMaxIdleMSec" =>
            if (v.isInstanceOf[Integer]) modelConfig.setSequenceMaxIdleMSec(v.asInstanceOf[Integer].longValue)
            else logger.warn("Invalid sequenceMaxIdleMSec: {}, should be positive int", v)
          case "sequenceTimeoutMSec" =>
            if (v.isInstanceOf[Integer]) modelConfig.setSequenceTimeoutMSec(v.asInstanceOf[Integer].longValue)
            else logger.warn("Invalid sequenceTimeoutMSec: {}, should be positive int", v)
          case "maxSequenceJobQueueSize" =>
            if (v.isInstanceOf[Integer]) modelConfig.setMaxSequenceJobQueueSize(v.asInstanceOf[Int])
            else logger.warn("Invalid maxSequenceJobQueueSize: {}, should be positive int", v)
          case "maxNumSequence" =>
            if (v.isInstanceOf[Integer]) modelConfig.setMaxNumSequence(v.asInstanceOf[Int])
            else logger.warn("Invalid maxNumSequence: {}, should be positive int", v)
          case "continuousBatching" =>
            if (v.isInstanceOf[Boolean]) modelConfig.setContinuousBatching(v.asInstanceOf[Boolean])
            else logger.warn("Invalid continuousBatching: {}, should be true or false", v)
          case "sequenceBatching" =>
            if (v.isInstanceOf[Boolean]) modelConfig.setSequenceBatching(v.asInstanceOf[Boolean])
            else logger.warn("Invalid sequenceBatching: {}, should be true or false", v)
          case "asyncCommunication" =>
            if (v.isInstanceOf[Boolean]) modelConfig.setAsyncCommunication(v.asInstanceOf[Boolean])
            else logger.warn("Invalid asyncCommunication: {}, should be true or false", v)
          case "useVenv" =>
            breakable(
              if (v.isInstanceOf[Boolean]) {
                modelConfig.setUseVenv(v.asInstanceOf[Boolean])
                break()
              }
              else logger.warn("Invalid useVenv: {}, should be true or false", v)

            )

          case _ => break()
        }
      })
    )

    modelConfig
  }
  enum ParallelType:
    case NONE, PP, TP, PPTP, CUSTOM
    
//  object ParallelType extends Enumeration {
//    type ParallelType = Value
//    val NONE, PP, TP, PPTP, CUSTOM = Value
//    private var `type` = nulldef
//    this (`type`: String) {
//      this ()
//      this.`type` = `type`.toLowerCase
//    }
//
//    def getParallelType: String = `type`
//
//    def get(parallelType: String): ModelConfig.ParallelType = {
//      var pType = NONE
//      try pType = util.Arrays.stream(ParallelType.values).filter((t: ModelConfig.ParallelType) => t.`type` == parallelType.toLowerCase).findFirst.get
//      catch {
//        case e: NoSuchElementException =>
//          logger.warn("Invalid ParallelType:{}", parallelType, e)
//      }
//      pType
//    }
//  }

  enum DeviceType:
    case NONE, CPU, GPU
//    def get(dt:String):DeviceType={
//      DeviceType.values.filter(dt)
//    }
    
//  object DeviceType extends Enumeration {
//    type DeviceType = Value
//    val NONE, CPU, GPU = Value
//    private var `type` = nulldef
//    this (`type`: String) {
//      this ()
//      this.`type` = `type`.toLowerCase
//    }
//
//    def getDeviceType: String = `type`
//
//    def get(deviceType: String): ModelConfig.DeviceType = {
//      var dType = DeviceType.NONE
//      try dType = util.Arrays.stream(DeviceType.values).filter((t: ModelConfig.DeviceType) => t.`type` == deviceType.toLowerCase).findFirst.get
//      catch {
//        case e: NoSuchElementException =>
//          logger.warn("Invalid DeviceType:{}", deviceType, e)
//      }
//      dType
//    }
//  }

  object TorchRun {
    def build(torchRunMap: Map[?, ?]): ModelConfig.TorchRun = {
      val torchRun = new ModelConfig.TorchRun
      torchRunMap.foreach((k, v) => {
        k.asInstanceOf[String] match {
          case "nnodes" =>
            if (v.isInstanceOf[Integer]) torchRun.setNnodes(v.asInstanceOf[Integer])
            else logger.warn("Invalid torchrun.nnodes:{}, reset to 1", v)
          case "nproc-per-node" =>
            if (v.isInstanceOf[Integer]) torchRun.setNprocPerNode(v.asInstanceOf[Integer])
            else logger.warn("Invalid torchrun.nproc-per-node:{}, reset to 1", v)
          case "rdzv-backend" =>
            if (v.isInstanceOf[String]) torchRun.setRdzvBackend(v.asInstanceOf[String])
            else logger.warn("Invalid torchrun.rdzv-backend:{}, reset to c10d", v)
          case "rdzv-endpoint" =>
            if (v.isInstanceOf[String]) torchRun.setRdzvEndpoint(v.asInstanceOf[String])
            else logger.warn("Invalid torchrun.rdzv-endpoint:{}", v)
          case "rdzv-conf" =>
            if (v.isInstanceOf[String]) torchRun.setRdzvConf(v.asInstanceOf[String])
            else logger.warn("Invalid torchrun.rdzv-conf:{}", v)
          case "monitor-interval" =>
            if (v.isInstanceOf[Integer]) torchRun.setMonitorInterval(v.asInstanceOf[Integer])
            else logger.warn("Invalid torchrun.max-restarts:{}, reset to 5", v)
          case "node-rank" =>
            if (v.isInstanceOf[Integer]) torchRun.setNodeRank(v.asInstanceOf[Integer])
            else logger.warn("Invalid torchrun.node-rank:{}, reset to 0", v)
          case "OMP_NUMBER_THREADS" =>
            if (v.isInstanceOf[Integer]) torchRun.setOmpNumberThreads(v.asInstanceOf[Integer])
            else logger.warn("Invalid OMP_NUMBER_THREADS:{}, reset to 1", v)
          case _ =>
            logger.warn("unsupported parameter {}", k)
        }
      })
      torchRun
    }
  }

  class TorchRun {
    private var nnodes = 1
    private var nprocPerNode = 1
    private var rdzvId: String = null
    private var rdzvEndpoint: String = null
    private var rdzvBackend = "c10d"
    private var rdzvConf: String = null
    private var monitorInterval = 5
    private var nodeRank = 0
    private var masterAddr: String = null
    private var masterPort = 0
    private var ompNumberThreads = 1

    def getNnodes: Int = nnodes

    def setNnodes(nnodes: Int): Unit = {
      if (nnodes <= 0) {
        logger.warn("Invalid torchrun.nnodes:{}, reset to 1", nnodes)
        return
      }
      this.nnodes = nnodes
    }

    def getNprocPerNode: Int = nprocPerNode

    def setNprocPerNode(nprocPerNode: Int): Unit = {
      if (nprocPerNode <= 0) {
        logger.warn("Invalid torchrun.nproc-per-node:{}, reset to 1", nprocPerNode)
        return
      }
      this.nprocPerNode = nprocPerNode
    }

    def getRdzvId: String = rdzvId

    def setRdzvId(rdzvId: String): Unit = {
      this.rdzvId = rdzvId
    }

    def getRdzvEndpoint: String = rdzvEndpoint

    def setRdzvEndpoint(rdzvEndpoint: String): Unit = {
      this.rdzvEndpoint = rdzvEndpoint
    }

    def getRdzvBackend: String = rdzvBackend

    def setRdzvBackend(rdzvBackend: String): Unit = {
      this.rdzvBackend = rdzvBackend
    }

    def getRdzvConf: String = rdzvConf

    def setRdzvConf(rdzvConf: String): Unit = {
      this.rdzvConf = rdzvConf
    }

    def getMonitorInterval: Int = monitorInterval

    def setMonitorInterval(monitorInterval: Int): Unit = {
      if (monitorInterval <= 0) {
        logger.warn("Invalid torchrun.monitor-interval:{}, reset to 5", monitorInterval)
        return
      }
      this.monitorInterval = monitorInterval
    }

    def getNodeRank: Int = nodeRank

    def setNodeRank(nodeRank: Int): Unit = {
      if (nodeRank < 0) {
        logger.warn("Invalid torchrun.node-rank:{}, reset to 0", nodeRank)
        return
      }
      this.nodeRank = nodeRank
    }

    def getMasterAddr: String = masterAddr

    def setMasterAddr(masterAddr: String): Unit = {
      this.masterAddr = masterAddr
    }

    def getMasterPort: Int = masterPort

    def setMasterPort(masterPort: Int): Unit = {
      this.masterPort = masterPort
    }

    def getOmpNumberThreads: Int = ompNumberThreads

    def setOmpNumberThreads(ompNumberThreads: Int): Unit = {
      if (ompNumberThreads < 1) {
        logger.warn("Invalid OMP_NUMBER_THREADS:{}, reset to 1", ompNumberThreads)
        return
      }
      this.ompNumberThreads = ompNumberThreads
    }
  }
}

class ModelConfig {
  /** the minimum number of workers of a model */
  private var minWorkers = 0
  /** the maximum number of workers of a model */
  private var maxWorkers = 0
  /** the batch size of a model */
  private var batchSize = 0
  /** the maximum delay in msec of a batch of a model */
  private var maxBatchDelay = 0
  /** the timeout in sec of a specific model's response. */
  var responseTimeout: Int = ModelConfig.defaultResponseTimeout
  /** the timeout in sec of a specific model's startup. */
  var startupTimeout: Int = ModelConfig.defaultStartupTimeout
  /**
   * the device type where the model is loaded. It can be gpu, cpu. The model is loaded on CPU if
   * deviceType: "cpu" is set on a GPU host.
   */
  private var deviceType = ModelConfig.DeviceType.NONE
  /**
   * the user specified gpu device id, By default, TorchServe auto round-robin all available GPUs
   * to assign deviceIds to a worker of a model if deviceIds is not set.
   */
  private var deviceIds: ListBuffer[Integer] = null
  /** this variable is auto calculated based on torchrun nproc-per-node. */
  private var parallelLevel = 0
  /** the model parallel type can be tp, pp, pptp */
  private var parallelType = ModelConfig.ParallelType.NONE
  /** torchrun config */
  private var torchRun: ModelConfig.TorchRun = null
  /** the maximum seconds of a worker recovery's timeout. default: 5 min */
  private var maxRetryTimeoutInSec = 300
  /**
   * the client timeout in milliseconds. The inference request will be dropped once it is timeout.
   * default: 0 which means no timeout (ie. clientExpireTS default value Long.MAX_VALUE.
   */
  private var clientTimeoutInMills = 0L
  /**
   * the job queue size of a model. By default, job_queue_size is set as 100 in config.property
   * for all models. Here, jobQueueSize: -1 means no customized setting for the model.
   */
  private var jobQueueSize = 0
  /**
   * the useJobTicket is a flag which allows an inference request to be accepted only if there are
   * available workers.
   */
  private var useJobTicket = false
  /**
   * the max idle in milliseconds of a sequence inference request of this stateful model. The
   * default value is 0.
   */
  private var sequenceMaxIdleMSec = 0L
  /**
   * the timeout of a sequence inference request of this stateful model. The default value is 0.
   */
  private var sequenceTimeoutMSec = 0L
  /**
   * the job queue size of one inference sequence of this stateful model. The default value is 1.
   */
  private var maxSequenceJobQueueSize = 1
  /** the max number of sequences can be accepted. The default value is 1. */
  private var maxNumSequence = 1
  /** continuousBatching is a flag to enable continuous batching. */
  private var continuousBatching = false
  /** asyncCommunication is a flag to enable async communication. */
  private var asyncCommunication = false
  /**
   * Create python virtual environment when using python backend to install model dependencies (if
   * enabled globally using configuration install_py_dep_per_model=true) and run workers for model
   * loading and inference.
   */
  private var useVenv = false
  /** sequenceBatching is a flag to enable https://github.com/pytorch/serve/issues/2743 */
  private var sequenceBatching = false

  def getMinWorkers: Int = minWorkers

  def setMinWorkers(minWorkers: Int): Unit = {
    this.minWorkers = Math.max(1, minWorkers)
  }

  def getMaxWorkers: Int = maxWorkers

  def setMaxWorkers(maxWorkers: Int): Unit = {
    if (maxWorkers < 0) {
      ModelConfig.logger.warn("Invalid maxWorkers:{}", maxWorkers)
      return
    }
    this.maxWorkers = maxWorkers
  }

  def getBatchSize: Int = batchSize

  def setBatchSize(batchSize: Int): Unit = {
    this.batchSize = Math.max(1, batchSize)
  }

  def getMaxBatchDelay: Int = maxBatchDelay

  def setMaxBatchDelay(maxBatchDelay: Int): Unit = {
    if (maxBatchDelay < 0) {
      ModelConfig.logger.warn("Invalid maxBatchDelay:{}", maxBatchDelay)
      return
    }
    this.maxBatchDelay = maxBatchDelay
  }

  def getResponseTimeout: Int = responseTimeout

  def setResponseTimeout(responseTimeout: Int): Unit = {
    if (responseTimeout <= 0) {
      ModelConfig.logger.warn("Invalid responseTimeout:{}", responseTimeout)
      return
    }
    this.responseTimeout = responseTimeout
  }

  def getStartupTimeout: Int = startupTimeout

  def setStartupTimeout(startupTimeout: Int): Unit = {
    if (startupTimeout <= 0) {
      ModelConfig.logger.warn("Invalid startupTimeout:{}", startupTimeout)
      return
    }
    this.startupTimeout = startupTimeout
  }

  def getDeviceIds: List[Integer] = deviceIds.toList

  def setDeviceIds(deviceIds: List[?]): Unit = {
    this.deviceIds = new ListBuffer[Integer]
    breakable(
      for (i <- 0 until deviceIds.size) {
        if (deviceIds(i).isInstanceOf[Integer]) this.deviceIds.append(deviceIds(i).asInstanceOf[Int])
        else {
          ModelConfig.logger.warn("Invalid deviceIds:{},", deviceIds(i))
          this.deviceIds = null
          break() //todo: break is not supported
        }
      }
    )

  }

  def getParallelLevel: Int = parallelLevel

  def setParallelLevel(parallelLevel: Int): Unit = {
    if (parallelLevel < 0) {
      ModelConfig.logger.warn("Invalid parallelLevel:{}, set as 0", parallelLevel)
      return
    }
    this.parallelLevel = parallelLevel
  }

  def setParallelMode(parallelMode: String): Unit = {
    this.parallelType = ModelConfig.ParallelType.valueOf(parallelMode)
  }

  def getParallelType: ModelConfig.ParallelType = this.parallelType

  def setDeviceType(deviceType: String): Unit = {
    this.deviceType = ModelConfig.DeviceType.valueOf(deviceType)
  }

  def getDeviceType: ModelConfig.DeviceType = deviceType

  def getTorchRun: ModelConfig.TorchRun = torchRun

  def getMaxRetryTimeoutInSec: Int = maxRetryTimeoutInSec

  def setMaxRetryTimeoutInSec(maxRetryTimeoutInSec: Int): Unit = {
    this.maxRetryTimeoutInSec = Math.max(0, maxRetryTimeoutInSec)
  }

  def getClientTimeoutInMills: Long = clientTimeoutInMills

  def setClientTimeoutInMills(clientTimeoutInMills: Long): Unit = {
    this.clientTimeoutInMills = Math.max(0, clientTimeoutInMills)
  }

  def getJobQueueSize: Int = jobQueueSize

  def setJobQueueSize(jobQueueSize: Int): Unit = {
    this.jobQueueSize = Math.max(0, jobQueueSize)
  }

  def isUseJobTicket: Boolean = useJobTicket

  def setUseJobTicket(useJobTicket: Boolean): Unit = {
    this.useJobTicket = useJobTicket
  }

  def getSequenceMaxIdleMSec: Long = sequenceMaxIdleMSec

  def setSequenceMaxIdleMSec(sequenceMaxIdleMSec: Long): Unit = {
    this.sequenceMaxIdleMSec = Math.max(0, sequenceMaxIdleMSec)
  }

  def getSequenceTimeoutMSec: Long = sequenceTimeoutMSec

  def setSequenceTimeoutMSec(sequenceTimeoutMSec: Long): Unit = {
    this.sequenceTimeoutMSec = Math.max(0, sequenceTimeoutMSec)
  }

  def getMaxSequenceJobQueueSize: Int = maxSequenceJobQueueSize

  def setMaxSequenceJobQueueSize(maxsequenceJobQueueSize: Int): Unit = {
    this.maxSequenceJobQueueSize = Math.max(1, maxsequenceJobQueueSize)
  }

  def isContinuousBatching: Boolean = continuousBatching

  def isAsyncCommunication: Boolean = asyncCommunication

  def setContinuousBatching(continuousBatching: Boolean): Unit = {
    this.continuousBatching = continuousBatching
  }

  def isSequenceBatching: Boolean = sequenceBatching

  def setSequenceBatching(sequenceBatching: Boolean): Unit = {
    this.sequenceBatching = sequenceBatching
  }

  def setAsyncCommunication(asyncCommunication: Boolean): Unit = {
    this.asyncCommunication = asyncCommunication
  }

  def getMaxNumSequence: Int = maxNumSequence

  def setMaxNumSequence(maxNumSequence: Int): Unit = {
    this.maxNumSequence = Math.max(1, maxNumSequence)
  }

  def getUseVenv: Boolean = useVenv

  def setUseVenv(useVenv: Boolean): Unit = {
    this.useVenv = useVenv
  }
}