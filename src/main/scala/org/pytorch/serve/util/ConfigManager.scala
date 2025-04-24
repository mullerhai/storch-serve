package org.pytorch.serve.util

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.ssl.{SslContext, SslContextBuilder}
import org.apache.commons.cli.{CommandLine, Option, Options}
import org.apache.commons.io.IOUtils
import org.pytorch.serve.archive.model.Manifest
import org.pytorch.serve.device.SystemInfo
import org.pytorch.serve.metrics.MetricBuilder
import org.pytorch.serve.servingsdk.snapshot.SnapshotSerializer
import org.pytorch.serve.snapshot.SnapshotSerializerFactory
import org.pytorch.serve.util.ConnectorType.{MANAGEMENT_CONNECTOR, METRICS_CONNECTOR}
import org.slf4j.{Logger, LoggerFactory}

import java.io.{File, IOException, InputStream}
import java.lang.reflect.{Field, Type}
import java.net.{InetAddress, UnknownHostException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.*
import java.security.cert.{Certificate, CertificateFactory, X509Certificate}
import java.security.spec.{InvalidKeySpecException, PKCS8EncodedKeySpec}
import java.util
import java.util.regex.{Matcher, Pattern, PatternSyntaxException}
import java.util.{Base64, InvalidPropertiesFormatException, Properties}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
import scala.util.control.Breaks.{break, breakable}
object ConfigManager {
  // Variables that can be configured through config.properties and Environment Variables
  // NOTE: Variables which can be configured through environment variables **SHOULD** have a
  // "TS_" prefix
  private val TS_DEBUG = "debug"
  private val TS_INFERENCE_ADDRESS = "inference_address"
  private val TS_MANAGEMENT_ADDRESS = "management_address"
  private val TS_METRICS_ADDRESS = "metrics_address"
  private val TS_LOAD_MODELS = "load_models"
  private val TS_BLACKLIST_ENV_VARS = "blacklist_env_vars"
  private val TS_DEFAULT_WORKERS_PER_MODEL = "default_workers_per_model"
  private val TS_DEFAULT_RESPONSE_TIMEOUT = "default_response_timeout"
  private val TS_DEFAULT_STARTUP_TIMEOUT = "default_startup_timeout"
  private val TS_UNREGISTER_MODEL_TIMEOUT = "unregister_model_timeout"
  private val TS_NUMBER_OF_NETTY_THREADS = "number_of_netty_threads"
  private val TS_NETTY_CLIENT_THREADS = "netty_client_threads"
  private val TS_JOB_QUEUE_SIZE = "job_queue_size"
  private val TS_NUMBER_OF_GPU = "number_of_gpu"
  private val TS_METRICS_CONFIG = "metrics_config"
  private val TS_METRICS_MODE = "metrics_mode"
  private val TS_MODEL_METRICS_AUTO_DETECT = "model_metrics_auto_detect"
  private val TS_DISABLE_SYSTEM_METRICS = "disable_system_metrics"
  // IPEX config option that can be set at config.properties
  private val TS_IPEX_ENABLE = "ipex_enable"
  private val TS_CPU_LAUNCHER_ENABLE = "cpu_launcher_enable"
  private val TS_CPU_LAUNCHER_ARGS = "cpu_launcher_args"
  private val TS_IPEX_GPU_ENABLE = "ipex_gpu_enable"
  private val TS_ASYNC_LOGGING = "async_logging"
  private val TS_CORS_ALLOWED_ORIGIN = "cors_allowed_origin"
  private val TS_CORS_ALLOWED_METHODS = "cors_allowed_methods"
  private val TS_CORS_ALLOWED_HEADERS = "cors_allowed_headers"
  private val TS_DECODE_INPUT_REQUEST = "decode_input_request"
  private val TS_KEYSTORE = "keystore"
  private val TS_KEYSTORE_PASS = "keystore_pass"
  private val TS_KEYSTORE_TYPE = "keystore_type"
  private val TS_CERTIFICATE_FILE = "certificate_file"
  private val TS_PRIVATE_KEY_FILE = "private_key_file"
  private val TS_MAX_REQUEST_SIZE = "max_request_size"
  private val TS_MAX_RESPONSE_SIZE = "max_response_size"
  private val TS_LIMIT_MAX_IMAGE_PIXELS = "limit_max_image_pixels"
  private val TS_DEFAULT_SERVICE_HANDLER = "default_service_handler"
  private val TS_SERVICE_ENVELOPE = "service_envelope"
  private val TS_MODEL_SERVER_HOME = "model_server_home"
  private val TS_MODEL_STORE = "model_store"
  private val TS_PREFER_DIRECT_BUFFER = "prefer_direct_buffer"
  private val TS_ALLOWED_URLS = "allowed_urls"
  private val TS_INSTALL_PY_DEP_PER_MODEL = "install_py_dep_per_model"
  private val TS_ENABLE_METRICS_API = "enable_metrics_api"
  private val TS_GRPC_INFERENCE_ADDRESS = "grpc_inference_address"
  private val TS_GRPC_MANAGEMENT_ADDRESS = "grpc_management_address"
  private val TS_GRPC_INFERENCE_PORT = "grpc_inference_port"
  private val TS_GRPC_MANAGEMENT_PORT = "grpc_management_port"
  private val TS_GRPC_INFERENCE_MAX_CONNECTION_AGE_MS = "grpc_inference_max_connection_age_ms"
  private val TS_GRPC_MANAGEMENT_MAX_CONNECTION_AGE_MS = "grpc_management_max_connection_age_ms"
  private val TS_GRPC_INFERENCE_MAX_CONNECTION_AGE_GRACE_MS = "grpc_inference_max_connection_age_grace_ms"
  private val TS_GRPC_MANAGEMENT_MAX_CONNECTION_AGE_GRACE_MS = "grpc_management_max_connection_age_grace_ms"
  private val TS_ENABLE_GRPC_SSL = "enable_grpc_ssl"
  private val TS_INITIAL_WORKER_PORT = "initial_worker_port"
  private val TS_INITIAL_DISTRIBUTION_PORT = "initial_distribution_port"
  private val TS_WORKFLOW_STORE = "workflow_store"
  private val TS_CPP_LOG_CONFIG = "cpp_log_config"
  private val TS_OPEN_INFERENCE_PROTOCOL = "ts_open_inference_protocol"
  private val TS_TOKEN_EXPIRATION_TIME_MIN = "token_expiration_min"
  private val TS_HEADER_KEY_SEQUENCE_ID = "ts_header_key_sequence_id"
  private val TS_HEADER_KEY_SEQUENCE_START = "ts_header_key_sequence_start"
  private val TS_HEADER_KEY_SEQUENCE_END = "ts_header_key_sequence_end"
  private val TS_DISABLE_TOKEN_AUTHORIZATION = "disable_token_authorization"
  private val TS_ENABLE_MODEL_API = "enable_model_api"
  // Configuration which are not documented or enabled through environment
  // variables
  private val USE_NATIVE_IO = "use_native_io"
  private val IO_RATIO = "io_ratio"
  private val METRIC_TIME_INTERVAL = "metric_time_interval"
  private val ENABLE_ENVVARS_CONFIG = "enable_envvars_config"
  private val MODEL_SNAPSHOT = "model_snapshot"
  private val MODEL_CONFIG = "models"
  private val VERSION = "version"
  private val SYSTEM_METRICS_CMD = "system_metrics_cmd"
  // Configuration default values
  private val DEFAULT_TS_ALLOWED_URLS = "file://.*|http(s)?://.*"
  private val USE_ENV_ALLOWED_URLS = "use_env_allowed_urls"
  // Variables which are local
  val MODEL_METRICS_LOGGER = "MODEL_METRICS"
  val MODEL_LOGGER = "MODEL_LOG"
  val MODEL_SERVER_METRICS_LOGGER = "TS_METRICS"
  val MODEL_SERVER_TELEMETRY_LOGGER = "TELEMETRY_METRICS"
  val METRIC_FORMAT_PROMETHEUS = "prometheus"
  val PYTHON_EXECUTABLE = "python"
  val DEFAULT_REQUEST_SEQUENCE_ID = "ts_request_sequence_id"
  val DEFAULT_REQUEST_SEQUENCE_START = "ts_request_sequence_start"
  val DEFAULT_REQUEST_SEQUENCE_END = "ts_request_sequence_end"
  val ADDRESS_PATTERN: Pattern = Pattern.compile("((https|http)://([^:^/]+)(:([0-9]+))?)|(unix:(/.*))", Pattern.CASE_INSENSITIVE)
  private val pattern = Pattern.compile("\\$\\$([^$]+[^$])\\$\\$")
  private var instance: ConfigManager = null
  private val logger = LoggerFactory.getLogger(classOf[ConfigManager])

  @throws[IOException]
  def readFile(path: String): String = Files.readString(Paths.get(path))

  @throws[IOException]
  def init(args: ConfigManager.Arguments): Unit = {
    instance = new ConfigManager(args)
  }

  def getInstance: ConfigManager = instance

  private def getCanonicalPath(file: File) = try file.getCanonicalPath
  catch {
    case e: IOException =>
      file.getAbsolutePath
  }

  private def getCanonicalPath(path: String): String = {
    if (path == null) return null
    getCanonicalPath(new File(path))
  }

  object Arguments {
    def getOptions: Options = {
      val options = new Options
      options.addOption(Option.builder("f").longOpt("ts-config-file").hasArg.argName("TS-CONFIG-FILE").desc("Path to the configuration properties file.").build)
      options.addOption(Option.builder("e").longOpt("python").hasArg.argName("PYTHON").desc("Python runtime executable path.").build)
      options.addOption(Option.builder("m").longOpt("models").hasArgs.argName("MODELS").desc("Models to be loaded at startup.").build)
      options.addOption(Option.builder("s").longOpt("model-store").hasArg.argName("MODELS-STORE").desc("Model store location where models can be loaded.").build)
      options.addOption(Option.builder("ncs").longOpt("no-config-snapshot").argName("NO-CONFIG-SNAPSHOT").desc("disable torchserve snapshot").build)
      options.addOption(Option.builder("w").longOpt("workflow-store").hasArg.argName("WORKFLOW-STORE").desc("Workflow store location where workflow can be loaded.").build)
      options.addOption(Option.builder("clog").longOpt("cpp-log-config").hasArg.argName("CPP-LOG-CONFIG").desc("log configuration file for cpp backend.").build)
      options.addOption(Option.builder("dt").longOpt("disable-token-auth").argName("TOKEN").desc("disables token authorization").build)
      options.addOption(Option.builder("mapi").longOpt("enable-model-api").argName("ENABLE-MODEL-API").desc("sets model apis to enabled").build)
      options
    }
  }

  final class Arguments {
    private var tsConfigFile: String = null
    private var pythonExecutable: String = null
    private var modelStore: String = null
    private var models: Array[String] = null
    private var snapshotDisabled = false
    private var workflowStore: String = null
    private var cppLogConfigFile: String = null
    private var tokenAuthEnabled = false
    private var modelApiEnabled = false

    def this(cmd: CommandLine) ={
      this()
      tsConfigFile = cmd.getOptionValue("ts-config-file")
      pythonExecutable = cmd.getOptionValue("python")
      modelStore = cmd.getOptionValue("model-store")
      models = cmd.getOptionValues("models")
      snapshotDisabled = cmd.hasOption("no-config-snapshot")
      workflowStore = cmd.getOptionValue("workflow-store")
      cppLogConfigFile = cmd.getOptionValue("cpp-log-config")
      tokenAuthEnabled = cmd.hasOption("disable-token-auth")
      modelApiEnabled = cmd.hasOption("enable-model-api")
    }

    def getTsConfigFile: String = tsConfigFile

    def getPythonExecutable: String = pythonExecutable

    def setTsConfigFile(tsConfigFile: String): Unit = {
      this.tsConfigFile = tsConfigFile
    }

    def getModelStore: String = modelStore

    def getWorkflowStore: String = workflowStore

    def isTokenDisabled: String = if (tokenAuthEnabled) "true"
    else "false"

    def setModelStore(modelStore: String): Unit = {
      this.modelStore = modelStore
    }

    def getModels: Array[String] = models

    def setModels(models: Array[String]): Unit = {
      this.models = models.clone
    }

    def isModelEnabled: String = String.valueOf(modelApiEnabled)

    def isSnapshotDisabled: Boolean = snapshotDisabled

    def setSnapshotDisabled(snapshotDisabled: Boolean): Unit = {
      this.snapshotDisabled = snapshotDisabled
    }

    def getCppLogConfigFile: String = cppLogConfigFile

    def setCppLogConfigFile(cppLogConfigFile: String): Unit = {
      this.cppLogConfigFile = cppLogConfigFile
    }
  }
}

@throws[IOException]
final class ConfigManager(args: ConfigManager.Arguments) {
  private var blacklistPattern: Pattern = null
  private var prop: Properties  = new Properties
  private var snapshotDisabled = false
  private var hostName: String = null
  private var modelConfig = new mutable.HashMap[String, Map[String, JsonObject]]
  private var torchrunLogDir: String = null
  private var telemetryEnabled = false
  private var headerKeySequenceId: String = null
  private var headerKeySequenceStart: String = null
  private var headerKeySequenceEnd: String = null
  var systemInfo: SystemInfo = new SystemInfo
  this.snapshotDisabled = args.isSnapshotDisabled
  var version = ConfigManager.readFile(getModelServerHome + "/ts/version.txt")
  if (version != null) {
    version = version.replaceAll("[\\n\\t ]", "")
    prop.setProperty(ConfigManager.VERSION, version)
  }
  val logLocation = System.getenv("LOG_LOCATION")
  if (logLocation != null) System.setProperty("LOG_LOCATION", logLocation)
  else if (System.getProperty("LOG_LOCATION") == null) System.setProperty("LOG_LOCATION", "logs")
  val metricsLocation = System.getenv("METRICS_LOCATION")
  if (metricsLocation != null) System.setProperty("METRICS_LOCATION", metricsLocation)
  else if (System.getProperty("METRICS_LOCATION") == null) System.setProperty("METRICS_LOCATION", "logs")
  var filePath = System.getenv("TS_CONFIG_FILE")
  var snapshotConfig: Properties = null
  if (filePath == null) {
    filePath = args.getTsConfigFile
    if (filePath == null) {
      snapshotConfig = getLastSnapshot
      if (snapshotConfig == null) filePath = System.getProperty("tsConfigFile", "config.properties")
      else prop.putAll(snapshotConfig)
    }
  }
  if (filePath != null) {
    val tsConfigFile = new File(filePath)
    if (tsConfigFile.exists) try {
      val stream = Files.newInputStream(tsConfigFile.toPath)
      try {
        prop.load(stream)
        prop.put("tsConfigFile", filePath)
      } catch {
        case e: IOException =>
          throw new IllegalStateException("Unable to read configuration file", e)
      } finally if (stream != null) stream.close()
    }
  }
  if (System.getenv("SM_TELEMETRY_LOG") != null) telemetryEnabled = true
  else telemetryEnabled = false
  resolveEnvVarVals(prop)
  val modelStore = args.getModelStore
  if (modelStore != null) prop.setProperty(ConfigManager.TS_MODEL_STORE, modelStore)
  val workflowStore = args.getWorkflowStore
  if (workflowStore != null) prop.setProperty(ConfigManager.TS_WORKFLOW_STORE, workflowStore)
  else if (prop.getProperty(ConfigManager.TS_WORKFLOW_STORE) == null) prop.setProperty(ConfigManager.TS_WORKFLOW_STORE, prop.getProperty(ConfigManager.TS_MODEL_STORE))
  val cppLogConfigFile = args.getCppLogConfigFile
  if (cppLogConfigFile != null) prop.setProperty(ConfigManager.TS_CPP_LOG_CONFIG, cppLogConfigFile)
  val models = args.getModels
  if (models != null) prop.setProperty(ConfigManager.TS_LOAD_MODELS,Seq(models).mkString(",") )
  if (args.isModelEnabled == "true") prop.setProperty(ConfigManager.TS_ENABLE_MODEL_API, args.isModelEnabled)
  val tokenDisabled = args.isTokenDisabled
  if (tokenDisabled == "true") prop.setProperty(ConfigManager.TS_DISABLE_TOKEN_AUTHORIZATION, tokenDisabled)
  prop.setProperty(ConfigManager.TS_NUMBER_OF_GPU, String.valueOf(Integer.min(this.systemInfo.getNumberOfAccelerators, getIntProperty(ConfigManager.TS_NUMBER_OF_GPU, Integer.MAX_VALUE))))
  val pythonExecutable = args.getPythonExecutable
  if (pythonExecutable != null) prop.setProperty(ConfigManager.PYTHON_EXECUTABLE, pythonExecutable)
  try {
    val ip = InetAddress.getLocalHost
    hostName = ip.getHostName
  } catch {
    case e: UnknownHostException =>
      hostName = "Unknown"
  }
  if (java.lang.Boolean.parseBoolean(prop.getProperty(ConfigManager.TS_ASYNC_LOGGING))) enableAsyncLogging()
  if (getEnableEnvVarsConfig) {
    // Environment variables have higher precedence over the config file variables
    setSystemVars()
  }
  setModelConfig()
  setTsHeaderKeySequenceId()
  setTsHeaderKeySequenceStart()
  setTsHeaderKeySequenceEnd()
  // Issue warnining about URLs that can be accessed when loading models
  if (prop.getProperty(ConfigManager.TS_ALLOWED_URLS, ConfigManager.DEFAULT_TS_ALLOWED_URLS) eq ConfigManager.DEFAULT_TS_ALLOWED_URLS) ConfigManager.logger.warn("Your torchserve instance can access any URL to load models. " + "When deploying to production, make sure to limit the set of allowed_urls in config.properties")


  private def resolveEnvVarVals(prop: Properties): Unit = {
    val keys = prop.stringPropertyNames
//    import scala.collection.JavaConversions._
    for (key <- keys.asScala) {
      val `val` = prop.getProperty(key)
      val matcher = ConfigManager.pattern.matcher(`val`)
      if (matcher.find) {
        val sb = new StringBuffer
        var condition = true
        while(condition){
          val envVar = matcher.group(1)
          if (System.getenv(envVar) == null) throw new IllegalArgumentException("Invalid Environment Variable " + envVar)
          matcher.appendReplacement(sb, System.getenv(envVar))
          condition = matcher.find
        }
//        do {
//          val envVar = matcher.group(1)
//          if (System.getenv(envVar) == null) throw new IllegalArgumentException("Invalid Environment Variable " + envVar)
//          matcher.appendReplacement(sb, System.getenv(envVar))
//        } while (matcher.find)
        matcher.appendTail(sb)
        prop.setProperty(key, sb.toString)
      }
    }
  }

  private def setSystemVars(): Unit = {
    val configClass = classOf[ConfigManager]
    val fields = configClass.getDeclaredFields
    for (f <- fields) {
      // For security, disable TS_ALLOWED_URLS in env.
      breakable(
        if ("TS_ALLOWED_URLS" == f.getName && !("true" == prop.getProperty(ConfigManager.USE_ENV_ALLOWED_URLS, "false").toLowerCase)) 
          //continue //todo: continue is not supported
          break()
      )
     
      if (f.getName.startsWith("TS_")) {
        val `val` = System.getenv(f.getName)
        if (`val` != null) try prop.setProperty(f.get(classOf[ConfigManager]).asInstanceOf[String], `val`)
        catch {
          case e: IllegalAccessException =>
            e.printStackTrace() // NOPMD
        }
      }
    }
  }

  def getEnableEnvVarsConfig: Boolean = java.lang.Boolean.getBoolean(prop.getProperty(ConfigManager.ENABLE_ENVVARS_CONFIG, "false"))

  def getHostName: String = hostName

  def isDebug: Boolean = java.lang.Boolean.getBoolean("TS_DEBUG") || java.lang.Boolean.parseBoolean(prop.getProperty(ConfigManager.TS_DEBUG, "false"))

  def getListener(connectorType: ConnectorType): Connector = {
    var binding: String = null
    connectorType match {
      case MANAGEMENT_CONNECTOR =>
        binding = prop.getProperty(ConfigManager.TS_MANAGEMENT_ADDRESS, "http://127.0.0.1:8081")
      case METRICS_CONNECTOR =>
        binding = prop.getProperty(ConfigManager.TS_METRICS_ADDRESS, "http://127.0.0.1:8082")
      case _ =>
        binding = prop.getProperty(ConfigManager.TS_INFERENCE_ADDRESS, "http://127.0.0.1:8080")
    }
    Connector.parse(binding, connectorType)
  }

  @throws[UnknownHostException]
  @throws[IllegalArgumentException]
  def getGRPCAddress(connectorType: ConnectorType): InetAddress = if (connectorType eq ConnectorType.MANAGEMENT_CONNECTOR) InetAddress.getByName(prop.getProperty(ConfigManager.TS_GRPC_MANAGEMENT_ADDRESS, "127.0.0.1"))
  else if (connectorType eq ConnectorType.INFERENCE_CONNECTOR) InetAddress.getByName(prop.getProperty(ConfigManager.TS_GRPC_INFERENCE_ADDRESS, "127.0.0.1"))
  else throw new IllegalArgumentException("Connector type not supported by gRPC: " + connectorType)

  @throws[IllegalArgumentException]
  def getGRPCPort(connectorType: ConnectorType): Int = {
    var port: String = null
    if (connectorType eq ConnectorType.MANAGEMENT_CONNECTOR) port = prop.getProperty(ConfigManager.TS_GRPC_MANAGEMENT_PORT, "7071")
    else if (connectorType eq ConnectorType.INFERENCE_CONNECTOR) port = prop.getProperty(ConfigManager.TS_GRPC_INFERENCE_PORT, "7070")
    else throw new IllegalArgumentException("Connector type not supported by gRPC: " + connectorType)
    port.toInt
  }

  @throws[IllegalArgumentException]
  def getGRPCMaxConnectionAge(connectorType: ConnectorType): Long = if (connectorType eq ConnectorType.MANAGEMENT_CONNECTOR) getLongProperty(ConfigManager.TS_GRPC_MANAGEMENT_MAX_CONNECTION_AGE_MS, Long.MaxValue)
  else if (connectorType eq ConnectorType.INFERENCE_CONNECTOR) getLongProperty(ConfigManager.TS_GRPC_INFERENCE_MAX_CONNECTION_AGE_MS, Long.MaxValue)
  else throw new IllegalArgumentException("Connector type not supported by gRPC: " + connectorType)

  @throws[IllegalArgumentException]
  def getGRPCMaxConnectionAgeGrace(connectorType: ConnectorType): Long = if (connectorType eq ConnectorType.MANAGEMENT_CONNECTOR) getLongProperty(ConfigManager.TS_GRPC_MANAGEMENT_MAX_CONNECTION_AGE_GRACE_MS, Long.MaxValue)
  else if (connectorType eq ConnectorType.INFERENCE_CONNECTOR) getLongProperty(ConfigManager.TS_GRPC_INFERENCE_MAX_CONNECTION_AGE_GRACE_MS, Long.MaxValue)
  else throw new IllegalArgumentException("Connector type not supported by gRPC: " + connectorType)

  def isOpenInferenceProtocol: Boolean = {
    val inferenceProtocol = System.getenv("TS_OPEN_INFERENCE_PROTOCOL")
    if (inferenceProtocol != null && (inferenceProtocol ne "")) return "oip" == inferenceProtocol
    java.lang.Boolean.parseBoolean(prop.getProperty(ConfigManager.TS_OPEN_INFERENCE_PROTOCOL, "false"))
  }

  def isGRPCSSLEnabled: Boolean = java.lang.Boolean.parseBoolean(getProperty(ConfigManager.TS_ENABLE_GRPC_SSL, "false"))

  def getPreferDirectBuffer: Boolean = java.lang.Boolean.parseBoolean(getProperty(ConfigManager.TS_PREFER_DIRECT_BUFFER, "false"))

  def getInstallPyDepPerModel: Boolean = java.lang.Boolean.parseBoolean(getProperty(ConfigManager.TS_INSTALL_PY_DEP_PER_MODEL, "false"))

  def isMetricApiEnable: Boolean = java.lang.Boolean.parseBoolean(getProperty(ConfigManager.TS_ENABLE_METRICS_API, "true"))

  def isCPULauncherEnabled: Boolean = java.lang.Boolean.parseBoolean(getProperty(ConfigManager.TS_CPU_LAUNCHER_ENABLE, "false"))

  def getCPULauncherArgs: String = getProperty(ConfigManager.TS_CPU_LAUNCHER_ARGS, null)

  def isIPEXGpuEnabled = java.lang.Boolean.parseBoolean(getProperty(ConfigManager.TS_IPEX_GPU_ENABLE, "false"))

  def getDisableTokenAuthorization: Boolean = java.lang.Boolean.parseBoolean(getProperty(ConfigManager.TS_DISABLE_TOKEN_AUTHORIZATION, "false"))

  def getNettyThreads: Int = getIntProperty(ConfigManager.TS_NUMBER_OF_NETTY_THREADS, 0)

  def getNettyClientThreads: Int = getIntProperty(ConfigManager.TS_NETTY_CLIENT_THREADS, 0)

  def getJobQueueSize: Int = getIntProperty(ConfigManager.TS_JOB_QUEUE_SIZE, 100)

  def getNumberOfGpu: Int = getIntProperty(ConfigManager.TS_NUMBER_OF_GPU, 0)

  def isModelApiEnabled: Boolean = java.lang.Boolean.parseBoolean(getProperty(ConfigManager.TS_ENABLE_MODEL_API, "false"))

  def getMetricsConfigPath: String = {
    var path = ConfigManager.getCanonicalPath(prop.getProperty(ConfigManager.TS_METRICS_CONFIG))
    if (path == null) path = getModelServerHome + "/ts/configs/metrics.yaml"
    path
  }

  def getTorchRunLogDir: String = {
    if (torchrunLogDir == null) torchrunLogDir = Paths.get(ConfigManager.getCanonicalPath(System.getProperty("LOG_LOCATION")), "torchelastic_ts").toString
    torchrunLogDir
  }

  def getMetricsMode: MetricBuilder.MetricMode = {
    val metricsMode = getProperty(ConfigManager.TS_METRICS_MODE, "log")
    try MetricBuilder.MetricMode.valueOf(metricsMode.replaceAll("\\s", "").toUpperCase)
    catch {
      case e@(_: IllegalArgumentException | _: NullPointerException) =>
        ConfigManager.logger.error("Configured metrics mode \"{}\" not supported. Defaulting to \"{}\" mode: {}", metricsMode, MetricBuilder.MetricMode.LOG, e)
        MetricBuilder.MetricMode.LOG
    }
  }

  def isModelMetricsAutoDetectEnabled: Boolean = java.lang.Boolean.parseBoolean(getProperty(ConfigManager.TS_MODEL_METRICS_AUTO_DETECT, "false"))

  def isSystemMetricsDisabled: Boolean = java.lang.Boolean.parseBoolean(getProperty(ConfigManager.TS_DISABLE_SYSTEM_METRICS, "false"))

  def getTsDefaultServiceHandler: String = getProperty(ConfigManager.TS_DEFAULT_SERVICE_HANDLER, null)

  def getTsServiceEnvelope: String = getProperty(ConfigManager.TS_SERVICE_ENVELOPE, null)

  def getConfiguration: Properties = prop.clone.asInstanceOf[Properties]

  def getConfiguredDefaultWorkersPerModel: Int = getIntProperty(ConfigManager.TS_DEFAULT_WORKERS_PER_MODEL, 0)

  def getDefaultWorkers: Int = {
    if (isDebug) return 1
    var workers = getConfiguredDefaultWorkersPerModel
    if (workers == 0) workers = getNumberOfGpu
    if (workers == 0) workers = Runtime.getRuntime.availableProcessors
    workers
  }

  def getMetricTimeInterval: Int = getIntProperty(ConfigManager.METRIC_TIME_INTERVAL, 60)

  def getModelServerHome: String = {
    var tsHome = System.getenv("TS_MODEL_SERVER_HOME")
    if (tsHome == null) {
      tsHome = System.getProperty(ConfigManager.TS_MODEL_SERVER_HOME)
      if (tsHome == null) {
        tsHome = getProperty(ConfigManager.TS_MODEL_SERVER_HOME, null)
        if (tsHome == null) {
          tsHome = ConfigManager.getCanonicalPath(findTsHome)
          return tsHome
        }
      }
    }
    val dir = new File(tsHome)
    if (!dir.exists) throw new IllegalArgumentException("Model server home not exist: " + tsHome)
    tsHome = ConfigManager.getCanonicalPath(dir)
    tsHome
  }

  def getPythonExecutable: String = prop.getProperty(ConfigManager.PYTHON_EXECUTABLE, "python")

  def getModelStore: String = ConfigManager.getCanonicalPath(prop.getProperty(ConfigManager.TS_MODEL_STORE))

  def getWorkflowStore: String = ConfigManager.getCanonicalPath(prop.getProperty(ConfigManager.TS_WORKFLOW_STORE))

  def getTsCppLogConfig: String = prop.getProperty(ConfigManager.TS_CPP_LOG_CONFIG, null)

  def getModelSnapshot: String = prop.getProperty(ConfigManager.MODEL_SNAPSHOT, null)

  def getLoadModels: String = prop.getProperty(ConfigManager.TS_LOAD_MODELS)

  def getBlacklistPattern: Pattern = blacklistPattern

  def getCorsAllowedOrigin: String = prop.getProperty(ConfigManager.TS_CORS_ALLOWED_ORIGIN)

  def getCorsAllowedMethods: String = prop.getProperty(ConfigManager.TS_CORS_ALLOWED_METHODS)

  def getCorsAllowedHeaders: String = prop.getProperty(ConfigManager.TS_CORS_ALLOWED_HEADERS)

  def getPrivateKeyFile: String = prop.getProperty(ConfigManager.TS_PRIVATE_KEY_FILE)

  def getCertificateFile: String = prop.getProperty(ConfigManager.TS_CERTIFICATE_FILE)

  def getSystemMetricsCmd: String = prop.getProperty(ConfigManager.SYSTEM_METRICS_CMD, "")

  @throws[IOException]
  @throws[GeneralSecurityException]
  def getSslContext = {
    val supportedCiphers = util.Arrays.asList("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
    var privateKey: PrivateKey = null
    var chain: Array[X509Certificate] = null
    val keyStoreFile = prop.getProperty(ConfigManager.TS_KEYSTORE)
    val privateKeyFile = prop.getProperty(ConfigManager.TS_PRIVATE_KEY_FILE)
    val certificateFile = prop.getProperty(ConfigManager.TS_CERTIFICATE_FILE)
    if (keyStoreFile != null) {
      val keystorePass = getProperty(ConfigManager.TS_KEYSTORE_PASS, "changeit").toCharArray
      val keystoreType = getProperty(ConfigManager.TS_KEYSTORE_TYPE, "PKCS12")
      val keyStore = KeyStore.getInstance(keystoreType)
      try {
        val is = Files.newInputStream(Paths.get(keyStoreFile))
        try keyStore.load(is, keystorePass)
        finally if (is != null) is.close()
      }
      val en = keyStore.aliases
      var keyAlias: String = null
      breakable(
        while (en.hasMoreElements) {
          val alias = en.nextElement
          if (keyStore.isKeyEntry(alias)) {
            keyAlias = alias
            break() //todo: break is not supported
          }
        }
      )

      if (keyAlias == null) throw new KeyException("No key entry found in keystore.")
      privateKey = keyStore.getKey(keyAlias, keystorePass).asInstanceOf[PrivateKey]
      val certs = keyStore.getCertificateChain(keyAlias)
      chain = new Array[X509Certificate](certs.length)
      for (i <- 0 until certs.length) {
        chain(i) = certs(i).asInstanceOf[X509Certificate]
      }
    }
    else if (privateKeyFile != null && certificateFile != null) {
      privateKey = loadPrivateKey(privateKeyFile)
      chain = loadCertificateChain(certificateFile)
    }
    else {
      val ssc = new SelfSignedCertificate
      privateKey = ssc.key
      chain = Array[X509Certificate](ssc.cert)
    }
    val sslConBuild = SslContextBuilder.forServer(privateKey, chain*)
    sslConBuild.protocols(Array[String]("TLSv1.2")*).ciphers(supportedCiphers).build
  }

  @throws[IOException]
  @throws[GeneralSecurityException]
  private def loadPrivateKey(keyFile: String) = {
    val keyFactory = KeyFactory.getInstance("RSA")
    try {
      val is = Files.newInputStream(Paths.get(keyFile))
      try {
        var content = IOUtils.toString(is, StandardCharsets.UTF_8)
        content = content.replaceAll("-----(BEGIN|END)( RSA)? PRIVATE KEY-----\\s*", "")
        var buf = Base64.getMimeDecoder.decode(content)
        try {
          val privKeySpec = new PKCS8EncodedKeySpec(buf)
          keyFactory.generatePrivate(privKeySpec)
        } catch {
          case e: InvalidKeySpecException =>

            // old private key is OpenSSL format private key
            buf = OpenSslKey.convertPrivateKey(buf)
            val privKeySpec = new PKCS8EncodedKeySpec(buf)
            keyFactory.generatePrivate(privKeySpec)
        }
      } finally if (is != null) is.close()
    }
  }

  @throws[IOException]
  @throws[GeneralSecurityException]
  private def loadCertificateChain(keyFile: String) = {
    val cf = CertificateFactory.getInstance("X.509")
    try {
      val is = Files.newInputStream(Paths.get(keyFile))
      try {
        val certs = cf.generateCertificates(is)
        var i = 0
        val chain = new Array[X509Certificate](certs.size)
//        import scala.collection.JavaConversions._
        for (cert <- certs.asScala) {
          chain({
            i += 1; i - 1
          }) = cert.asInstanceOf[X509Certificate]
        }
        chain
      } finally if (is != null) is.close()
    }
  }

  private def getLastSnapshot: Properties = {
    if (isSnapshotDisabled) return null
    val serializer = SnapshotSerializerFactory.getSerializer
    serializer.getLastSnapshot
  }

  def getProperty(key: String, `def`: String) = prop.getProperty(key, `def`)

  @throws[InvalidPropertiesFormatException]
  def validateConfigurations(): Unit = {
    val blacklistVars = prop.getProperty(ConfigManager.TS_BLACKLIST_ENV_VARS, "")
    try blacklistPattern = Pattern.compile(blacklistVars)
    catch {
      case e: PatternSyntaxException =>
        throw new InvalidPropertiesFormatException(e)
    }
  }

  def dumpConfigurations = {
    val runtime = Runtime.getRuntime
    "\nTorchserve version: " + prop.getProperty(ConfigManager.VERSION) + "\nTS Home: " + getModelServerHome + "\nCurrent directory: " + ConfigManager.getCanonicalPath(".") + "\nTemp directory: " + System.getProperty("java.io.tmpdir") + "\nMetrics config path: " + getMetricsConfigPath + "\nNumber of GPUs: " + getNumberOfGpu + "\nNumber of CPUs: " + runtime.availableProcessors + "\nMax heap size: " + (runtime.maxMemory / 1024 / 1024) + " M\nPython executable: " + (if (getPythonExecutable == null) "N/A"
    else getPythonExecutable) + "\nConfig file: " + prop.getProperty("tsConfigFile", "N/A") + "\nInference address: " + getListener(ConnectorType.INFERENCE_CONNECTOR) + "\nManagement address: " + getListener(ConnectorType.MANAGEMENT_CONNECTOR) + "\nMetrics address: " + getListener(ConnectorType.METRICS_CONNECTOR) + "\nModel Store: " + (if (getModelStore == null) "N/A"
    else getModelStore) + "\nInitial Models: " + (if (getLoadModels == null) "N/A"
    else getLoadModels) + "\nLog dir: " + ConfigManager.getCanonicalPath(System.getProperty("LOG_LOCATION")) + "\nMetrics dir: " + ConfigManager.getCanonicalPath(System.getProperty("METRICS_LOCATION")) + "\nNetty threads: " + getNettyThreads + "\nNetty client threads: " + getNettyClientThreads + "\nDefault workers per model: " + getDefaultWorkers + "\nBlacklist Regex: " + prop.getProperty(ConfigManager.TS_BLACKLIST_ENV_VARS, "N/A") + "\nMaximum Response Size: " + prop.getProperty(ConfigManager.TS_MAX_RESPONSE_SIZE, "6553500") + "\nMaximum Request Size: " + prop.getProperty(ConfigManager.TS_MAX_REQUEST_SIZE, "6553500") + "\nLimit Maximum Image Pixels: " + prop.getProperty(ConfigManager.TS_LIMIT_MAX_IMAGE_PIXELS, "true") + "\nPrefer direct buffer: " + prop.getProperty(ConfigManager.TS_PREFER_DIRECT_BUFFER, "false") + "\nAllowed Urls: " + getAllowedUrls + "\nCustom python dependency for model allowed: " + prop.getProperty(ConfigManager.TS_INSTALL_PY_DEP_PER_MODEL, "false") + "\nEnable metrics API: " + prop.getProperty(ConfigManager.TS_ENABLE_METRICS_API, "true") + "\nMetrics mode: " + getMetricsMode + "\nDisable system metrics: " + isSystemMetricsDisabled + "\nWorkflow Store: " + (if (getWorkflowStore == null) "N/A"
    else getWorkflowStore) + "\nCPP log config: " + (if (getTsCppLogConfig == null) "N/A"
    else getTsCppLogConfig) + "\nModel config: " + prop.getProperty(ConfigManager.MODEL_CONFIG, "N/A") + "\nSystem metrics command: " + (if (getSystemMetricsCmd.isEmpty) "default"
    else getSystemMetricsCmd) + "\nModel API enabled: " + (if (isModelApiEnabled) "true"
    else "false")
  }

  def useNativeIo = java.lang.Boolean.parseBoolean(prop.getProperty(ConfigManager.USE_NATIVE_IO, "true"))

  def getIoRatio = getIntProperty(ConfigManager.IO_RATIO, 50)

  def getMaxResponseSize = getIntProperty(ConfigManager.TS_MAX_RESPONSE_SIZE, 6553500)

  def getMaxRequestSize = getIntProperty(ConfigManager.TS_MAX_REQUEST_SIZE, 6553500)

  def isLimitMaxImagePixels = java.lang.Boolean.parseBoolean(prop.getProperty(ConfigManager.TS_LIMIT_MAX_IMAGE_PIXELS, "true"))

  def setProperty(key: String, value: String): Unit = {
    prop.setProperty(key, value)
  }

  private def getIntProperty(key: String, `def`: Int): Int = {
    val value = prop.getProperty(key)
    if (value == null) return `def`
    value.toInt
  }

  private def getLongProperty(key: String, `def`: Long): Long = {
    val value = prop.getProperty(key)
    if (value == null) return `def`
    java.lang.Long.parseLong(value)
  }

  def getDefaultResponseTimeout = prop.getProperty(ConfigManager.TS_DEFAULT_RESPONSE_TIMEOUT, "120").toInt

  def getDefaultStartupTimeout = prop.getProperty(ConfigManager.TS_DEFAULT_STARTUP_TIMEOUT, "120").toInt

  def getUnregisterModelTimeout = prop.getProperty(ConfigManager.TS_UNREGISTER_MODEL_TIMEOUT, "120").toInt

  private def findTsHome: File = {
    val cwd = new File(ConfigManager.getCanonicalPath("."))
    var file = cwd
    while (file != null) {
      val ts = new File(file, "ts")
      if (ts.exists) return file
      file = file.getParentFile
    }
    cwd
  }

  private def enableAsyncLogging(): Unit = {
    System.setProperty("log4j2.contextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector")
  }

  def getBackendConfiguration = {
    val config = new mutable.HashMap[String, String]
    // Append properties used by backend worker here
    config.put("TS_DECODE_INPUT_REQUEST", prop.getProperty(ConfigManager.TS_DECODE_INPUT_REQUEST, "true"))
    config.put("TS_IPEX_ENABLE", prop.getProperty(ConfigManager.TS_IPEX_ENABLE, "false"))
    config.put("TS_IPEX_GPU_ENABLE", prop.getProperty(ConfigManager.TS_IPEX_GPU_ENABLE, "false"))
    config
  }

  def getAllowedUrls: List[String] = {
    val allowedURL: String = prop.getProperty(ConfigManager.TS_ALLOWED_URLS, ConfigManager.DEFAULT_TS_ALLOWED_URLS)
    //    util.Arrays.asList(allowedURL.split(",")*)
    allowedURL.split(",").toList
  }

  def isSnapshotDisabled: Boolean = snapshotDisabled

  def getTimeToExpiration: Double = {
    if (prop.getProperty(ConfigManager.TS_TOKEN_EXPIRATION_TIME_MIN) != null) try return java.lang.Double.valueOf(prop.getProperty(ConfigManager.TS_TOKEN_EXPIRATION_TIME_MIN))
    catch {
      case e: NumberFormatException =>
        ConfigManager.logger.error("Token expiration not a valid integer")
    }
    60.0
  }

  def getTsHeaderKeySequenceId = this.headerKeySequenceId

  def setTsHeaderKeySequenceId(): Unit = {
    this.headerKeySequenceId = prop.getProperty(ConfigManager.TS_HEADER_KEY_SEQUENCE_ID, ConfigManager.DEFAULT_REQUEST_SEQUENCE_ID)
  }

  def getTsHeaderKeySequenceStart = this.headerKeySequenceStart

  def setTsHeaderKeySequenceStart(): Unit = {
    this.headerKeySequenceStart = prop.getProperty(ConfigManager.TS_HEADER_KEY_SEQUENCE_START, ConfigManager.DEFAULT_REQUEST_SEQUENCE_START)
  }

  def getTsHeaderKeySequenceEnd = this.headerKeySequenceEnd

  def setTsHeaderKeySequenceEnd(): Unit = {
    this.headerKeySequenceEnd = prop.getProperty(ConfigManager.TS_HEADER_KEY_SEQUENCE_END, ConfigManager.DEFAULT_REQUEST_SEQUENCE_END)
  }

  def isSSLEnabled(connectorType: ConnectorType) = {
    var address = prop.getProperty(ConfigManager.TS_INFERENCE_ADDRESS, "http://127.0.0.1:8080")
    connectorType match {
      case MANAGEMENT_CONNECTOR =>
        address = prop.getProperty(ConfigManager.TS_MANAGEMENT_ADDRESS, "http://127.0.0.1:8081")
      case METRICS_CONNECTOR =>
        address = prop.getProperty(ConfigManager.TS_METRICS_ADDRESS, "http://127.0.0.1:8082")
      case _ =>
    }
    // String inferenceAddress = prop.getProperty(TS_INFERENCE_ADDRESS,
    // "http://127.0.0.1:8080");
    val matcher = ConfigManager.ADDRESS_PATTERN.matcher(address)
    if (!matcher.matches) throw new IllegalArgumentException("Invalid binding address: " + address)
    val protocol = matcher.group(2)
    "https".equalsIgnoreCase(protocol)
  }

  def getInitialWorkerPort = prop.getProperty(ConfigManager.TS_INITIAL_WORKER_PORT, "9000").toInt

  def setInitialWorkerPort(initialPort: Int): Unit = {
    prop.setProperty(ConfigManager.TS_INITIAL_WORKER_PORT, String.valueOf(initialPort))
  }

  def getInitialDistributionPort = prop.getProperty(ConfigManager.TS_INITIAL_DISTRIBUTION_PORT, "29500").toInt

  def setInitialDistributionPort(initialPort: Int): Unit = {
    prop.setProperty(ConfigManager.TS_INITIAL_DISTRIBUTION_PORT, String.valueOf(initialPort))
  }

  def getJsonIntValue(modelName: String, version: String, element: String, defaultVal: Int): Int = {
    var value = defaultVal
    if (this.modelConfig.contains(modelName)) {
      val versionModel = this.modelConfig.get(modelName).get
      val jsonObject: JsonObject = versionModel.getOrElse(version, null)
      if (jsonObject != null && jsonObject.get(element) != null) try {
        value = jsonObject.get(element).getAsInt
        if (value <= 0) value = defaultVal
      } catch {
        case e@(_: ClassCastException | _: IllegalStateException) =>
          LoggerFactory.getLogger(classOf[ConfigManager]).error("Invalid value for model: {}:{}, parameter: {}", modelName, version, element)
          return defaultVal
      }
    }
    value
  }

  def getJsonRuntimeTypeValue(modelName: String, version: String, element: String, defaultVal: Manifest.RuntimeType): Manifest.RuntimeType = {
    var value = defaultVal
    if (this.modelConfig.contains(modelName)) {
      val versionModel = this.modelConfig.get(modelName).get
      val jsonObject: JsonObject = versionModel.getOrElse(version, null)
      if (jsonObject != null && jsonObject.get(element) != null) try value = Manifest.RuntimeType.valueOf(jsonObject.get(element).getAsString)
      catch {
        case e@(_: ClassCastException | _: IllegalStateException | _: IllegalArgumentException) =>
          LoggerFactory.getLogger(classOf[ConfigManager]).error("Invalid value for model: {}:{}, parameter: {}", modelName, version, element)
          return defaultVal
      }
    }
    value
  }

  private def setModelConfig(): Unit = {
    val modelConfigStr = prop.getProperty(ConfigManager.MODEL_CONFIG, null)
    val `type` = new TypeToken[Map[String, Map[String, JsonObject]]]() {}.getType
    if (modelConfigStr != null) this.modelConfig = JsonUtils.GSON.fromJson(modelConfigStr, `type`)
  }

  def getVersion = prop.getProperty(ConfigManager.VERSION)

  def isTelemetryEnabled = telemetryEnabled
}