package org.pytorch.serve.http

/**
 * Constructs an {@code ResourceNotFoundException} with {@code null} as its error detail
 * message.
 */
@SerialVersionUID(1L)
class ResourceNotFoundException extends RuntimeException("Requested resource is not found, please refer to API document.") {
}