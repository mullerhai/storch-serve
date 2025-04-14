package org.pytorch.serve

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.ServerInterceptors
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.FixedRecvByteBufAllocator
import io.netty.channel.ServerChannel
import io.netty.handler.ssl.SslContext
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.Slf4JLoggerFactory

import java.io.File
import java.io.IOException
import java.lang.annotation.Annotation
import java.net.InetSocketAddress
import java.security.GeneralSecurityException
import java.util
import java.util.InvalidPropertiesFormatException
import java.util.ServiceLoader
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.ModelArchive
import org.pytorch.serve.archive.model.ModelException
import org.pytorch.serve.archive.model.ModelNotFoundException
import org.pytorch.serve.grpcimpl.GRPCInterceptor
import org.pytorch.serve.grpcimpl.GRPCServiceFactory
import org.pytorch.serve.http.messages.RegisterModelRequest
import org.pytorch.serve.metrics.MetricCache
import org.pytorch.serve.metrics.MetricManager
import org.pytorch.serve.servingsdk.ModelServerEndpoint
import org.pytorch.serve.servingsdk.annotations.Endpoint
import org.pytorch.serve.servingsdk.annotations.helpers.EndpointTypes
import org.pytorch.serve.servingsdk.impl.PluginsManager
import org.pytorch.serve.snapshot.InvalidSnapshotException
import org.pytorch.serve.snapshot.SnapshotManager
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.Connector
import org.pytorch.serve.util.ConnectorType
import org.pytorch.serve.util.ServerGroups
import org.pytorch.serve.util.TokenAuthorization
import org.pytorch.serve.wlm.Model
import org.pytorch.serve.wlm.ModelManager
import org.pytorch.serve.wlm.WorkLoadManager
import org.pytorch.serve.wlm.WorkerInitializationException
import org.pytorch.serve.workflow.WorkflowManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.*
import scala.util.control.Breaks.{break, breakable}
object ModelServer {
  val MAX_RCVBUF_SIZE = 4096

  def main(args: Array[String]): Unit = {
    val options = ConfigManager.Arguments.getOptions
    try {
      val parser = new DefaultParser
      val cmd = parser.parse(options, args, null, false)
      val arguments = new ConfigManager.Arguments(cmd)
      ConfigManager.init(arguments)
      val configManager = ConfigManager.getInstance
      TokenAuthorization.init()
      PluginsManager.getInstance.initialize()
      MetricCache.init()
      InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE)
      val modelServer = new ModelServer(configManager)
      Runtime.getRuntime.addShutdownHook(new Thread() {
        override def run(): Unit = {
          modelServer.stop()
        }
      })
      modelServer.startAndWait()
    } catch {
      case e: IllegalArgumentException =>
        System.out.println("Invalid configuration: " + e.getMessage) // NOPMD
      case e: ParseException =>
        val formatter = new HelpFormatter
        formatter.setLeftPadding(1)
        formatter.setWidth(120)
        formatter.printHelp(e.getMessage, options)
      case t: Throwable =>
        t.printStackTrace() // NOPMD
    } finally System.exit(1) // NOPMD
  }
}

class ModelServer(configManager: ConfigManager){
  
  private val logger = LoggerFactory.getLogger(classOf[ModelServer])
  private var serverGroups: ServerGroups = new ServerGroups(configManager)
  private var inferencegRPCServer: Server = null
  private var managementgRPCServer: Server = null
  private val OIPgRPCServer: Server = null
  private val futures = new util.ArrayList[ChannelFuture](2)
  private val stopped = new AtomicBoolean(false)
//  val configManager: ConfigManager
  @throws[InterruptedException]
  @throws[IOException]
  @throws[GeneralSecurityException]
  @throws[InvalidSnapshotException]
  def startAndWait(): Unit = {
    try {
      val channelFutures = startRESTserver
      startGRPCServers()
      // Create and schedule metrics manager
      if (!configManager.isSystemMetricsDisabled) MetricManager.scheduleMetrics(configManager)
      System.out.println("Model server started.") // NOPMD
      channelFutures.get(0).sync
    } catch {
      case e: InvalidPropertiesFormatException =>
        logger.error("Invalid configuration", e)
    } finally {
      serverGroups.shutdown(true)
      logger.info("Torchserve stopped.")
    }
  }

  def getDefaultModelName(name: String) = if (name.contains(".model") || name.contains(".mar")) name.substring(name.lastIndexOf('/') + 1, name.lastIndexOf('.')).replaceAll("(\\W|^_)", "_")
  else name.substring(name.lastIndexOf('/') + 1).replaceAll("(\\W|^_)", "_")

  @throws[InvalidSnapshotException]
  @throws[IOException]
  def initModelStore(): Unit = {
    val wlm = new WorkLoadManager(configManager, serverGroups.getBackendGroup)
    ModelManager.init(configManager, wlm)
    WorkflowManager.init(configManager)
    SnapshotManager.init(configManager)
    val startupModels = ModelManager.getInstance.getStartupModels
    var defaultModelName: String = null
    val modelSnapshot = configManager.getModelSnapshot
    if (modelSnapshot != null) {
      SnapshotManager.getInstance.restore(modelSnapshot)
      return
    }
    val loadModels = configManager.getLoadModels
    if (loadModels == null || loadModels.isEmpty) return
    val modelManager = ModelManager.getInstance
    val workers = configManager.getDefaultWorkers
    if ("ALL".equalsIgnoreCase(loadModels)) {
      val modelStore = configManager.getModelStore
      if (modelStore == null) {
        logger.warn("Model store is not configured.")
        return
      }
      val modelStoreDir = new File(modelStore)
      if (!modelStoreDir.exists) {
        logger.warn("Model store path is not found: {}", modelStore)
        return
      }
      // Check folders to see if they can be models as well
      val files = modelStoreDir.listFiles
      if (files != null) for (file <- files) {
        breakable(
          if (file.isHidden) 
            break()
            //continue //todo: continue is not supported
        )
        
        val fileName = file.getName
        breakable(
        if (file.isFile && !fileName.endsWith(".mar") && !fileName.endsWith(".model") && !fileName.endsWith(".tar.gz")) break()//continue //todo: continue is not supported
        )
        try {
          logger.debug("Loading models from model store: {}", file.getName)
          defaultModelName = getDefaultModelName(fileName)
          val archive = modelManager.registerModel(file.getName, defaultModelName)
          var minWorkers = configManager.getJsonIntValue(archive.getModelName, archive.getModelVersion, Model.MIN_WORKERS, workers)
          var maxWorkers = configManager.getJsonIntValue(archive.getModelName, archive.getModelVersion, Model.MAX_WORKERS, workers)
          if (archive.getModelConfig != null) {
            val marMinWorkers = archive.getModelConfig.getMinWorkers
            val marMaxWorkers = archive.getModelConfig.getMaxWorkers
            if (marMinWorkers > 0 && marMaxWorkers >= marMinWorkers) {
              minWorkers = marMinWorkers
              maxWorkers = marMaxWorkers
            }
          }
          modelManager.updateModel(archive.getModelName, archive.getModelVersion, minWorkers, maxWorkers, true, false)
          startupModels.add(archive.getModelName)
        } catch {
          case e@(_: ModelException | _: IOException | _: InterruptedException | _: DownloadArchiveException | _: WorkerInitializationException) =>
            logger.warn("Failed to load model: " + file.getAbsolutePath, e)
        }
      }
      return
    }
    val models = loadModels.split(",")
    for (model <- models) {
      val pair = model.split("=", 2)
      var modelName: String = null
      var url: String = null
      if (pair.length == 1) url = pair(0)
      else {
        modelName = pair(0)
        url = pair(1)
      }
      breakable(
        if (url.isEmpty) break() // continue //todo: continue is not supported
      )
      
      try {
        logger.info("Loading initial models: {}", url)
        defaultModelName = getDefaultModelName(url)
        val archive = modelManager.registerModel(url, modelName, null, null, -1 * RegisterModelRequest.DEFAULT_BATCH_SIZE, -1 * RegisterModelRequest.DEFAULT_MAX_BATCH_DELAY, configManager.getDefaultResponseTimeout, configManager.getDefaultStartupTimeout, defaultModelName, false, false, false)
        var minWorkers = configManager.getJsonIntValue(archive.getModelName, archive.getModelVersion, Model.MIN_WORKERS, workers)
        var maxWorkers = configManager.getJsonIntValue(archive.getModelName, archive.getModelVersion, Model.MAX_WORKERS, workers)
        if (archive.getModelConfig != null) {
          val marMinWorkers = archive.getModelConfig.getMinWorkers
          val marMaxWorkers = archive.getModelConfig.getMaxWorkers
          if (marMinWorkers > 0 && marMaxWorkers >= marMinWorkers) {
            minWorkers = marMinWorkers
            maxWorkers = marMaxWorkers
          }
          else logger.warn("Invalid model config in mar, minWorkers:{}, maxWorkers:{}", marMinWorkers, marMaxWorkers)
        }
        modelManager.updateModel(archive.getModelName, archive.getModelVersion, minWorkers, maxWorkers, true, false)
        startupModels.add(archive.getModelName)
      } catch {
        case e@(_: ModelException | _: IOException | _: InterruptedException | _: DownloadArchiveException | _: WorkerInitializationException) =>
          logger.warn("Failed to load model: " + url, e)
      }
    }
  }

  @throws[InterruptedException]
  @throws[IOException]
  @throws[GeneralSecurityException]
  def initializeServer(connector: Connector, serverGroup: EventLoopGroup, workerGroup: EventLoopGroup, `type`: ConnectorType): ChannelFuture = {
    val purpose = connector.getPurpose
    val channelClass = connector.getServerChannel
    logger.info("Initialize {} server with: {}.", purpose, channelClass.getSimpleName)
    val b = new ServerBootstrap
    b.option(ChannelOption.SO_BACKLOG, 1024).channel(channelClass).childOption(ChannelOption.SO_LINGER, 0).childOption(ChannelOption.SO_REUSEADDR, true).childOption(ChannelOption.SO_KEEPALIVE, true).childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(ModelServer.MAX_RCVBUF_SIZE))
    b.group(serverGroup, workerGroup)
    var sslCtx: SslContext = null
    if (connector.isSsl) sslCtx = configManager.getSslContext
    b.childHandler(new ServerInitializer(sslCtx, `type`))
    var future: ChannelFuture = null
    try future = b.bind(connector.getSocketAddress).sync
    catch {
      case e: Exception =>

        // https://github.com/netty/netty/issues/2597
        if (e.isInstanceOf[IOException]) throw new IOException("Failed to bind to address: " + connector, e)
        throw e
    }
    future.addListener((f: ChannelFuture) => {
      if (!f.isSuccess) {
        try f.get
        catch {
          case e@(_: InterruptedException | _: ExecutionException) =>
            logger.error("", e)
        }
        System.exit(-1) // NO PMD
      }
      serverGroups.registerChannel(f.channel)
    }.asInstanceOf[ChannelFutureListener])
    future.sync
    val f = future.channel.closeFuture
    f.addListener((listener: ChannelFuture) => logger.info("{} model server stopped.", purpose).asInstanceOf[ChannelFutureListener])
    logger.info("{} API bind to: {}", purpose, connector)
    f
  }

  /**
   * Main Method that prepares the future for the channel and sets up the ServerBootstrap.
   *
   * @return A ChannelFuture object
   * @throws InterruptedException if interrupted
   * @throws InvalidSnapshotException
   */
  @throws[InterruptedException]
  @throws[IOException]
  @throws[GeneralSecurityException]
  @throws[InvalidSnapshotException]
  def startRESTserver: util.List[ChannelFuture] = {
    stopped.set(false)
    configManager.validateConfigurations()
    logger.info(configManager.dumpConfigurations)
    initModelStore()
    val inferenceConnector = configManager.getListener(ConnectorType.INFERENCE_CONNECTOR)
    val managementConnector = configManager.getListener(ConnectorType.MANAGEMENT_CONNECTOR)
    inferenceConnector.clean()
    managementConnector.clean()
    val serverGroup = serverGroups.getServerGroup
    val workerGroup = serverGroups.getChildGroup
    futures.clear()
    if (!(inferenceConnector == managementConnector)) {
      futures.add(initializeServer(inferenceConnector, serverGroup, workerGroup, ConnectorType.INFERENCE_CONNECTOR))
      futures.add(initializeServer(managementConnector, serverGroup, workerGroup, ConnectorType.MANAGEMENT_CONNECTOR))
    }
    else futures.add(initializeServer(inferenceConnector, serverGroup, workerGroup, ConnectorType.ALL))
    if (configManager.isMetricApiEnable) {
      val metricsGroup = serverGroups.getMetricsGroup
      val metricsConnector = configManager.getListener(ConnectorType.METRICS_CONNECTOR)
      metricsConnector.clean()
      futures.add(initializeServer(metricsConnector, serverGroup, metricsGroup, ConnectorType.METRICS_CONNECTOR))
    }
    SnapshotManager.getInstance.saveStartupSnapshot()
    futures
  }

  @throws[IOException]
  def startGRPCServers(): Unit = {
    inferencegRPCServer = startGRPCServer(ConnectorType.INFERENCE_CONNECTOR)
    managementgRPCServer = startGRPCServer(ConnectorType.MANAGEMENT_CONNECTOR)
  }

  @throws[IOException]
  def startGRPCServer(connectorType: ConnectorType) = {
    val s = NettyServerBuilder.forAddress(new InetSocketAddress(
        configManager.getGRPCAddress(connectorType), 
        configManager.getGRPCPort(connectorType)))
      .maxConnectionAge(configManager.getGRPCMaxConnectionAge(connectorType), TimeUnit.MILLISECONDS)
      .maxConnectionAgeGrace(configManager.getGRPCMaxConnectionAgeGrace(connectorType), TimeUnit.MILLISECONDS)
      .maxInboundMessageSize(configManager.getMaxRequestSize)
      .addService(ServerInterceptors.intercept(GRPCServiceFactory.getgRPCService(connectorType), new GRPCInterceptor(connectorType)))
    if ((connectorType eq ConnectorType.INFERENCE_CONNECTOR) && ConfigManager.getInstance.isOpenInferenceProtocol) s.maxInboundMessageSize(configManager.getMaxRequestSize).addService(ServerInterceptors.intercept(GRPCServiceFactory.getgRPCService(ConnectorType.OPEN_INFERENCE_CONNECTOR), new GRPCInterceptor(connectorType)))
    if (configManager.isGRPCSSLEnabled) s.useTransportSecurity(new File(configManager.getCertificateFile), new File(configManager.getPrivateKeyFile))
    val server = s.build
    server.start
    server
  }

  def validEndpoint(a: Annotation, `type`: EndpointTypes) = a.isInstanceOf[Endpoint] && !(a.asInstanceOf[Endpoint]).urlPattern.isEmpty && a.asInstanceOf[Endpoint].endpointType == `type`

  def registerEndpoints(`type`: EndpointTypes) = {
    val loader = ServiceLoader.load(classOf[ModelServerEndpoint])
    val ep = new util.HashMap[String, ModelServerEndpoint]
//    import scala.collection.JavaConversions._
    for (mep <- loader.asScala) {
      val modelServerEndpointClassObj = mep.getClass
      val annotations = modelServerEndpointClassObj.getAnnotations
      for (a <- annotations) {
        if (validEndpoint(a, `type`)) ep.put(a.asInstanceOf[Endpoint].urlPattern, mep)
      }
    }
    ep
  }

  def isRunning: Boolean = !stopped.get

  def stopgRPCServer(server: Server): Unit = {
    if (server != null) try server.shutdown.awaitTermination()
    catch {
      case e: InterruptedException =>
        e.printStackTrace() // NOPMD
    }
  }

  @throws[ModelNotFoundException]
   def exitModelStore(): Unit = {
    val modelMgr = ModelManager.getInstance
    val defModels = modelMgr.getDefaultModels
//    import scala.collection.JavaConversions._
    for (m <- defModels.entrySet.asScala) {
      val versionModels = modelMgr.getAllModelVersions(m.getKey)
      val defaultVersionId = m.getValue.getVersion
//      import scala.collection.JavaConversions._
      for (versionedModel <- versionModels.asScala) {
        breakable(
        if (defaultVersionId == versionedModel.getKey) //
          break()
          // continue //todo: continue is not supported
        )
        logger.info("Unregistering model {} version {}", versionedModel.getValue.getModelName, versionedModel.getKey)
        modelMgr.unregisterModel(versionedModel.getValue.getModelName, versionedModel.getKey, true)
      }
      logger.info("Unregistering model {} version {}", m.getValue.getModelName, defaultVersionId)
      modelMgr.unregisterModel(m.getValue.getModelName, defaultVersionId, true)
    }
  }

  def stop(): Unit = {
    if (stopped.get) return
    stopped.set(true)
    stopgRPCServer(inferencegRPCServer)
    stopgRPCServer(managementgRPCServer)
//    import scala.collection.JavaConversions._
    for (future <- futures.asScala) {
      try future.channel.close.sync
      catch {
        case ignore: InterruptedException =>
          ignore.printStackTrace() // NOPMD
      }
    }
    SnapshotManager.getInstance.saveShutdownSnapshot()
    serverGroups.shutdown(true)
    serverGroups.init()
    try exitModelStore()
    catch {
      case e: Exception =>
        e.printStackTrace() // NOPMD
    }
  }
}