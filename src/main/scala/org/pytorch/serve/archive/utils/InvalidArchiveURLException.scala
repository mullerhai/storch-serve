package org.pytorch.serve.archive.utils

@SerialVersionUID(1L)
class InvalidArchiveURLException (message: String, cause: Throwable)  extends Exception(message, cause)  {
  /**
   * Constructs an {@code InvalidArchiveURLException} with the specified detail message.
   *
   * @param message The detail message (which is saved for later retrieval by the {@link 
 *     #getMessage()} method)
   */
  def this(message: String) = this(message, null)

  def this(cause: Throwable) = this(Option(cause).map(_.getMessage).orNull, cause)

  /**
   * Constructs an {@code InvalidArchiveURLException} with the specified detail message and cause.
   *
   * <p>Note that the detail message associated with {@code cause} is <i>not</i> automatically
   * incorporated into this exception's detail message.
   *
   * @param message The detail message (which is saved for later retrieval by the {@link 
 *     #getMessage()} method)
   * @param cause   The cause (which is saved for later retrieval by the {@link # getCause ( )}
   *                method). (A null value is permitted, and indicates that the cause is nonexistent or
   *                unknown.)
   */
//  def this(message: String, cause: Throwable) ={
//    this()
//    super (message, cause)
//  }
}