package org.pytorch.serve.http

/**
 * Constructs an {@code MethodNotAllowedException} with {@code null} as its error detail
 * message.
 */
@SerialVersionUID(1L)
class MethodNotAllowedException extends RuntimeException("Requested method is not allowed, please refer to API document.") {
}