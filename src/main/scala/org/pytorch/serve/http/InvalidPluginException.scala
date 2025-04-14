package org.pytorch.serve.http

/** InvaliPluginException is thrown when there is an error while handling a Model Server plugin */
@SerialVersionUID(1L)
class InvalidPluginException(msg: String="Registered plugin is invalid. Please re-check the configuration and the plugins.") extends RuntimeException(msg) {
  /**
   * Constructs an {@code InvalidPluginException} with {@code msg} as its error detail message
   *
   * @param msg : This is the error detail message
   */
//  def this(msg: String) ={
//    this()
//    super (msg)
//  }
}