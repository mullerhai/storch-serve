package org.pytorch.serve.http

/**
 * Constructs an {@code ServiceUnavailableException} with the specified detail message.
 *
 * @param message The detail message (which is saved for later retrieval by the {@link  *     #getMessage()} method)
 */
@SerialVersionUID(1L)
class ServiceUnavailableException(message: String) extends RuntimeException(message) {
}