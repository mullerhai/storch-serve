package org.pytorch.serve.openapi

import io.netty.handler.codec.http.HttpHeaderValues
import io.prometheus.client.exporter.common.TextFormat
import java.util
import org.pytorch.serve.archive.model.Manifest
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.ConnectorType
import org.pytorch.serve.util.JsonUtils
import org.pytorch.serve.wlm.Model
import scala.jdk.CollectionConverters._
object OpenApiUtils {
  def listApis(`type`: ConnectorType): String = {
    val openApi = new OpenApi
    val info = new Info
    info.setTitle("TorchServe APIs")
    info.setDescription("TorchServe is a flexible and easy to use tool for serving deep learning models")
    val config = ConfigManager.getInstance
    info.setVersion(config.getProperty("version", null))
    openApi.setInfo(info)
    if (ConnectorType.ALL == `type` || ConnectorType.INFERENCE_CONNECTOR == `type`) listInferenceApis(openApi)
    if (ConnectorType.ALL == `type` || ConnectorType.MANAGEMENT_CONNECTOR == `type`) listManagementApis(openApi)
    openApi.addPath("/metrics", getMetricsPath)
    JsonUtils.GSON_PRETTY.toJson(openApi)
  }

  /**
   * The /v1/models/{model_name}:predict prediction api is used to access torchserve from kserve
   * v1 protocol and /v2/models/{model_name}/infer is for kserve v2 protocol.
   */
  private def listInferenceApis(openApi: OpenApi): Unit = {
    openApi.addPath("/", getApiDescriptionPath("apiDescription", false))
    openApi.addPath("/ping", getPingPath)
    openApi.addPath("/v1/models/{model_name}:predict", getPredictionsPath(false))
    openApi.addPath("/v2/models/{model_name}/infer", getPredictionsPath(false))
    openApi.addPath("/predictions/{model_name}", getPredictionsPath(false))
    openApi.addPath("/predictions/{model_name}/{model_version}", getPredictionsPath(true))
    openApi.addPath("/api-description", getApiDescriptionPath("api-description", true))
  }

  private def listManagementApis(openApi: OpenApi): Unit = {
    openApi.addPath("/", getApiDescriptionPath("apiDescription", false))
    openApi.addPath("/models", getModelsPath)
    openApi.addPath("/models/{model_name}", getModelManagerPath(false))
    openApi.addPath("/models/{model_name}/{model_version}", getModelManagerPath(true))
    openApi.addPath("/models/{model_name}/{model_version}/set-default", getSetDefaultPath)
    openApi.addPath("/api-description", getApiDescriptionPath("api-description", true))
  }

  def getModelApi(model: Model): String = {
    val modelName = model.getModelName
    val openApi = new OpenApi
    val info = new Info
    info.setTitle("RESTful API for: " + modelName)
    info.setVersion("1.0.0")
    openApi.setInfo(info)
    openApi.addPath("/prediction/" + modelName, getModelPath(modelName))
    openApi.addPath("/v1/models/{model_name}:predict", getModelPath(modelName))
    openApi.addPath("/v2/models/{model_name}/infer", getModelPath(modelName))
    JsonUtils.GSON_PRETTY.toJson(openApi)
  }

  def getModelManagementApi(model: Model): String = {
    val modelName = model.getModelName
    val openApi = new OpenApi
    val info = new Info
    info.setTitle("RESTful Management API for: " + modelName)
    val config = ConfigManager.getInstance
    info.setVersion(config.getProperty("version", null))
    openApi.setInfo(info)
    openApi.addPath("/models/{model_name}", getModelManagerPath(false))
    openApi.addPath("/models/{model_name}/{model_version}", getModelManagerPath(true))
    openApi.addPath("/models/{model_name}/{model_version}/set-default", getSetDefaultPath)
    JsonUtils.GSON_PRETTY.toJson(openApi)
  }

  private def getApiDescriptionPath(operationID: String, legacy: Boolean) = {
    val schema = new Schema("object")
    schema.addProperty("openapi", new Schema("string"), true)
    schema.addProperty("info", new Schema("object"), true)
    schema.addProperty("paths", new Schema("object"), true)
    val mediaType = new MediaType(HttpHeaderValues.APPLICATION_JSON.toString, schema)
    val operation = new Operation(operationID, "Get openapi description.")
    operation.addResponse(new Response("200", "A openapi 3.0.1 descriptor", mediaType))
    operation.addResponse(new Response("500", "Internal Server Error", getErrorResponse))
    val path = new Path
    if (legacy) {
      operation.setDeprecated(true)
      path.setGet(operation)
    }
    else path.setOptions(operation)
    path
  }

  private def getPingPath = {
    val schema = new Schema("object")
    schema.addProperty("status", new Schema("string", "Overall status of the TorchServe."), true)
    val mediaType = new MediaType(HttpHeaderValues.APPLICATION_JSON.toString, schema)
    val operation = new Operation("ping", "Get TorchServe status.")
    operation.addResponse(new Response("200", "TorchServe status", mediaType))
    operation.addResponse(new Response("500", "Internal Server Error", getErrorResponse))
    val path = new Path
    path.setGet(operation)
    path
  }

  private def getPredictionsPath(version: Boolean) = {
    var operationDescription: String = null
    var operationId: String = null
    if (version) {
      operationDescription = "Predictions entry point to get inference using specific model version."
      operationId = "version_predictions"
    }
    else {
      operationDescription = "Predictions entry point to get inference using default model version."
      operationId = "predictions"
    }
    val post = new Operation(operationId, operationDescription)
    post.addParameter(new PathParameter("model_name", "Name of model."))
    if (version) post.addParameter(new PathParameter("model_version", "Name of model version."))
    var schema = new Schema("string")
    schema.setFormat("binary")
    var mediaType = new MediaType("*/*", schema)
    val requestBody = new RequestBody
    requestBody.setDescription("Input data format is defined by each model.")
    requestBody.setRequired(true)
    requestBody.addContent(mediaType)
    post.setRequestBody(requestBody)
    schema = new Schema("string")
    schema.setFormat("binary")
    mediaType = new MediaType("*/*", schema)
    val resp = new Response("200", "Output data format is defined by each model.", mediaType)
    post.addResponse(resp)
    val error = getErrorResponse
    post.addResponse(new Response("404", "Model not found or Model Version not found", error))
    post.addResponse(new Response("500", "Internal Server Error", error))
    post.addResponse(new Response("503", "No worker is available to serve request", error))
    val path = new Path
    path.setPost(post)
    path
  }

  private def getModelsPath = {
    val path = new Path
    path.setGet(getListModelsOperation)
    path.setPost(getRegisterOperation)
    path
  }

  private def getSetDefaultPath = {
    val path = new Path
    path.setPut(getSetDefaultOperation)
    path
  }

  private def getModelManagerPath(version: Boolean) = {
    val path = new Path
    path.setGet(getDescribeModelOperation(version))
    path.setPut(getScaleOperation(version))
    path.setDelete(getUnRegisterOperation(version))
    path
  }

  private def getSetDefaultOperation = {
    val operation = new Operation("setDefault", "Set default version of a model")
    operation.addParameter(new PathParameter("model_name", "Name of model whose default version needs to be updated."))
    operation.addParameter(new PathParameter("model_version", "Version of model to be set as default version for the model"))
    val status = getStatusResponse
    val error = getErrorResponse
    operation.addResponse(new Response("200", "Default version successfully updated for model", status))
    operation.addResponse(new Response("404", "Model not found or Model version not found", error))
    operation.addResponse(new Response("500", "Internal Server Error", error))
    operation
  }

  private def getListModelsOperation = {
    val operation = new Operation("listModels", "List registered models in TorchServe.")
    operation.addParameter(new QueryParameter("limit", "integer", "100", "Use this parameter to specify the maximum number of items to return. When" + " this value is present, TorchServe does not return more than the specified" + " number of items, but it might return fewer. This value is optional. If you" + " include a value, it must be between 1 and 1000, inclusive. If you do not" + " include a value, it defaults to 100."))
    operation.addParameter(new QueryParameter("next_page_token", "The token to retrieve the next set of results. TorchServe provides the" + " token when the response from a previous call has more results than the" + " maximum page size."))
    val schema = new Schema("object")
    schema.addProperty("nextPageToken", new Schema("string", "Use this parameter in a subsequent request after you receive a response" + " with truncated results. Set it to the value of NextMarker from the" + " truncated response you just received."), false)
    val modelProp = new Schema("object")
    modelProp.addProperty("modelName", new Schema("string", "Name of the model."), true)
    modelProp.addProperty("modelUrl", new Schema("string", "URL of the model."), true)
    val modelsProp = new Schema("array", "A list of registered models.")
    modelsProp.setItems(modelProp)
    schema.addProperty("models", modelsProp, true)
    val json = new MediaType(HttpHeaderValues.APPLICATION_JSON.toString, schema)
    operation.addResponse(new Response("200", "OK", json))
    operation.addResponse(new Response("500", "Internal Server Error", getErrorResponse))
    operation
  }

  private def getRegisterOperation = {
    val operation = new Operation("registerModel", "Register a new model in TorchServe.")
    operation.addParameter(new QueryParameter("url", "string", null, true, "Model archive download url, support local file or HTTP(s) protocol." + " For S3, consider use pre-signed url."))
    operation.addParameter(new QueryParameter("model_name", "Name of model. This value will override modelName in MANIFEST.json if present."))
    operation.addParameter(new QueryParameter("handler", "Inference handler entry-point. This value will override handler in MANIFEST.json if present."))
    val runtime = new QueryParameter("runtime", "Runtime for the model custom service code. This value will override runtime in MANIFEST.json if present.")
    operation.addParameter(runtime)
    operation.addParameter(new QueryParameter("batch_size", "integer", "1", "Inference batch size, default: 1."))
    operation.addParameter(new QueryParameter("max_batch_delay", "integer", "100", "Maximum delay for batch aggregation, default: 100."))
    operation.addParameter(new QueryParameter("response_timeout", "integer", "2", "Maximum time, in seconds, the TorchServe waits for a response from the model inference code, default: 120."))
    operation.addParameter(new QueryParameter("startup_timeout", "integer", "120", "Maximum time, in seconds, the TorchServe waits for the model to startup/initialize, default: 120."))
    operation.addParameter(new QueryParameter("initial_workers", "integer", "0", "Number of initial workers, default: 0."))
    operation.addParameter(new QueryParameter("synchronous", "boolean", "false", "Decides whether creation of worker synchronous or not, default: false."))
    operation.addParameter(new QueryParameter("s3_sse_kms", "boolean", "false", "Model mar file is S3 SSE KMS(server side encryption) enabled or not, default: false."))
    val types = Manifest.RuntimeType.values
    val runtimeTypes = new util.ArrayList[String](types.length)
    for (`type` <- types) {
      runtimeTypes.add(`type`.toString)
    }
    runtime.getSchema.setEnumeration(runtimeTypes)
    val status = getStatusResponse
    val error = getErrorResponse
    operation.addResponse(new Response("200", "Model registered", status))
    operation.addResponse(new Response("202", "Accepted", status))
    operation.addResponse(new Response("210", "Partial Success", status))
    operation.addResponse(new Response("400", "Bad request", error))
    operation.addResponse(new Response("404", "Model not found", error))
    operation.addResponse(new Response("409", "Model already registered", error))
    operation.addResponse(new Response("500", "Internal Server Error", error))
    operation
  }

  private def getUnRegisterOperation(version: Boolean) = {
    var operationDescription: String = null
    var operationId: String = null
    if (version) {
      operationDescription = "Unregister the specified version of a model from TorchServe. " + "This is an asynchronous call by default. Caller can call listModels to confirm model is unregistered"
      operationId = "version_unregisterModel"
    }
    else {
      operationDescription = "Unregister the default version of a model from TorchServe if it is the only version available. " + "This is an asynchronous call by default. Caller can call listModels to confirm model is unregistered."
      operationId = "unregisterModel"
    }
    val operation = new Operation(operationId, operationDescription)
    operation.addParameter(new PathParameter("model_name", "Name of model to unregister."))
    if (version) operation.addParameter(new PathParameter("model_version", "Version of model to unregister."))
    operation.addParameter(new QueryParameter("synchronous", "boolean", "false", "Decides whether the call is synchronous or not, default: false."))
    operation.addParameter(new QueryParameter("timeout", "integer", "-1", "Waiting up to the specified wait time if necessary for" + " a worker to complete all pending requests. Use 0 to terminate backend" + " worker process immediately. Use -1 for wait infinitely."))
    val status = getStatusResponse
    val error = getErrorResponse
    operation.addResponse(new Response("200", "Model unregistered", status))
    operation.addResponse(new Response("202", "Accepted", status))
    operation.addResponse(new Response("404", "Model not found or Model version not found", error))
    operation.addResponse(new Response("408", "Request Timeout Error", error))
    operation.addResponse(new Response("500", "Internal Server Error", error))
    operation
  }

  private def getDescribeModelOperation(version: Boolean) = {
    var operationDescription: String = null
    var operationId: String = null
    if (version) {
      operationDescription = "Provides detailed information about the specified version of a model." + "If \"all\" is specified as version, returns the details about all the versions of the model."
      operationId = "version_describeModel"
    }
    else {
      operationDescription = "Provides detailed information about the default version of a model."
      operationId = "describeModel"
    }
    val operation = new Operation(operationId, operationDescription)
    operation.addParameter(new PathParameter("model_name", "Name of model to describe."))
    if (version) operation.addParameter(new PathParameter("model_version", "Version of model to describe."))
    val schema = new Schema("object")
    schema.addProperty("modelName", new Schema("string", "Name of the model."), true)
    schema.addProperty("modelVersion", new Schema("string", "Version of the model."), true)
    schema.addProperty("modelUrl", new Schema("string", "URL of the model."), true)
    schema.addProperty("minWorkers", new Schema("integer", "Configured minimum number of worker."), true)
    schema.addProperty("maxWorkers", new Schema("integer", "Configured maximum number of worker."), true)
    schema.addProperty("batchSize", new Schema("integer", "Configured batch size."), false)
    schema.addProperty("maxBatchDelay", new Schema("integer", "Configured maximum batch delay in ms."), false)
    schema.addProperty("status", new Schema("string", "Overall health status of the model"), true)
    val workers = new Schema("array", "A list of active backend workers.")
    val worker = new Schema("object")
    worker.addProperty("id", new Schema("string", "Worker id"), true)
    worker.addProperty("startTime", new Schema("string", "Worker start time"), true)
    worker.addProperty("gpu", new Schema("boolean", "If running on GPU"), false)
    val workerStatus = new Schema("string", "Worker status")
    val status = new util.ArrayList[String]
    status.add("READY")
    status.add("LOADING")
    status.add("UNLOADING")
    workerStatus.setEnumeration(status)
    worker.addProperty("status", workerStatus, true)
    workers.setItems(worker)
    schema.addProperty("workers", workers, true)
    val metrics = new Schema("object")
    metrics.addProperty("rejectedRequests", new Schema("integer", "Number requests has been rejected in last 10 minutes."), true)
    metrics.addProperty("waitingQueueSize", new Schema("integer", "Number requests waiting in the queue."), true)
    metrics.addProperty("requests", new Schema("integer", "Number requests processed in last 10 minutes."), true)
    schema.addProperty("metrics", metrics, true)
    val jobQueueStatus = new Schema("object")
    jobQueueStatus.addProperty("remainingCapacity", new Schema("integer", "Number of new requests that can be queued."), true)
    jobQueueStatus.addProperty("pendingRequests", new Schema("integer", "Number of requests waiting in the queue."), true)
    schema.addProperty("jobQueueStatus", jobQueueStatus, true)
    val mediaType = new MediaType(HttpHeaderValues.APPLICATION_JSON.toString, schema)
    val error = getErrorResponse
    operation.addResponse(new Response("200", "OK", mediaType))
    operation.addResponse(new Response("404", "Model not found or Model version not found", error))
    operation.addResponse(new Response("500", "Internal Server Error", error))
    operation
  }

  private def getScaleOperation(version: Boolean) = {
    var operationDescription: String = null
    var operationId: String = null
    if (version) {
      operationDescription = "Configure number of workers for a specified version of a model. " + "This is an asynchronous call by default. Caller need to call describeModel to check if the model workers has been changed."
      operationId = "version_setAutoScale"
    }
    else {
      operationDescription = "Configure number of workers for a default version of a model. " + "This is an asynchronous call by default. Caller need to call describeModel to check if the model workers has been changed."
      operationId = "setAutoScale"
    }
    val operation = new Operation(operationId, operationDescription)
    operation.addParameter(new PathParameter("model_name", "Name of model to scale workers."))
    if (version) operation.addParameter(new PathParameter("model_version", "Version of model to scale workers."))
    operation.addParameter(new QueryParameter("min_worker", "integer", "1", "Minimum number of worker processes."))
    operation.addParameter(new QueryParameter("max_worker", "integer", "1", "Maximum number of worker processes."))
    operation.addParameter(new QueryParameter("number_gpu", "integer", "0", "Number of GPU worker processes to create."))
    operation.addParameter(new QueryParameter("synchronous", "boolean", "false", "Decides whether the call is synchronous or not, default: false."))
    operation.addParameter(new QueryParameter("timeout", "integer", "-1", "Waiting up to the specified wait time if necessary for" + " a worker to complete all pending requests. Use 0 to terminate backend" + " worker process immediately. Use -1 for wait infinitely."))
    val status = getStatusResponse
    val error = getErrorResponse
    operation.addResponse(new Response("200", "Model workers updated", status))
    operation.addResponse(new Response("202", "Accepted", status))
    operation.addResponse(new Response("210", "Partial Success", status))
    operation.addResponse(new Response("400", "Bad request", error))
    operation.addResponse(new Response("404", "Model not found or Model version not found", error))
    operation.addResponse(new Response("500", "Internal Server Error", error))
    operation
  }

  private def getModelPath(modelName: String) = {
    val operation = new Operation(modelName, "A predict entry point for model: " + modelName + '.')
    operation.addResponse(new Response("200", "OK"))
    operation.addResponse(new Response("500", "Internal Server Error", getErrorResponse))
    val path = new Path
    path.setPost(operation)
    path
  }

  private def getMetricsPath = {
    val schema = new Schema("object")
    schema.addProperty("# HELP", new Schema("string", "Help text for TorchServe metric."), true)
    schema.addProperty("# TYPE", new Schema("string", "Type of TorchServe metric."), true)
    schema.addProperty("metric", new Schema("string", "TorchServe application metric."), true)
    val mediaType = new MediaType(TextFormat.CONTENT_TYPE_004, schema)
    val operation = new Operation("metrics", "Get TorchServe application metrics in prometheus format.")
    operation.addParameter(new QueryParameter("name[]", "Names of metrics to filter"))
    operation.addResponse(new Response("200", "TorchServe application metrics", mediaType))
    operation.addResponse(new Response("500", "Internal Server Error", getErrorResponse))
    val path = new Path
    path.setGet(operation)
    path
  }

  private def getErrorResponse = {
    val schema = new Schema("object")
    schema.addProperty("code", new Schema("integer", "Error code."), true)
    schema.addProperty("type", new Schema("string", "Error type."), true)
    schema.addProperty("message", new Schema("string", "Error message."), true)
    new MediaType(HttpHeaderValues.APPLICATION_JSON.toString, schema)
  }

  private def getStatusResponse = {
    val schema = new Schema("object")
    schema.addProperty("status", new Schema("string", "Error type."), true)
    new MediaType(HttpHeaderValues.APPLICATION_JSON.toString, schema)
  }
}

final class OpenApiUtils private {
}