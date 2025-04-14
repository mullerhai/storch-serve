package org.pytorch.serve.util

import io.grpc.Status
import scala.jdk.CollectionConverters._

object GRPCUtils {
  def getRegisterParam(param: String, `def`: String): String = {
    if ("" == param) return `def`
    param
  }

  def getRegisterParam(param: Int, `def`: Int): Int = {
    if (param > 0) return param
    `def`
  }

  def getGRPCStatusCode(httpStatusCode: Int): Status = httpStatusCode match {
    case 400 =>
      Status.INVALID_ARGUMENT
    case 401 =>
      Status.UNAUTHENTICATED
    case 403 =>
      Status.PERMISSION_DENIED
    case 404 =>
      Status.NOT_FOUND
    case 409 =>
      Status.ABORTED
    case 413 =>  Status.RESOURCE_EXHAUSTED
    case 429 =>
      Status.RESOURCE_EXHAUSTED
    case 416 =>
      Status.OUT_OF_RANGE
    case 499 =>
      Status.CANCELLED
    case 504 =>
      Status.DEADLINE_EXCEEDED
    case 501 =>
      Status.UNIMPLEMENTED
    case 503 =>
      Status.UNAVAILABLE
    case _ =>
      if (httpStatusCode >= 200 && httpStatusCode < 300) return Status.OK
      if (httpStatusCode >= 400 && httpStatusCode < 500) return Status.FAILED_PRECONDITION
      if (httpStatusCode >= 500 && httpStatusCode < 600) return Status.INTERNAL
      Status.UNKNOWN
  }
}

final class GRPCUtils private {
}