package org.pytorch.serve.wlm

@SerialVersionUID(1L)
class WorkerInitializationException(message: String, cause: Throwable)  extends Exception(message, cause)  {
  /** Creates a new {@code WorkerInitializationException} instance. */
//    def this(message: String)= {
//      this()
//      super (message)
//    }

  def this(message: String) = this(message, null)

  def this(cause: Throwable) = this(Option(cause).map(_.getMessage).orNull, cause)
  /**
   * Constructs a new {@code WorkerInitializationException} with the specified detail message and
   * cause.
   *
   * @param message the detail message (which is saved for later retrieval by the {@link 
 *     #getMessage()} method).
   * @param cause   the cause (which is saved for later retrieval by the {@link # getCause ( )}
   *                method). (A <tt>null</tt> value is permitted, and indicates that the cause is nonexistent
   *                or unknown.)
   */
//  def this(message: String, cause: Throwable) ={
//    this()
//    super (message, cause)
//  }
}