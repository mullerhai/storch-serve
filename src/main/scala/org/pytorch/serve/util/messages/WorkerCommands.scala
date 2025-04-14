package org.pytorch.serve.util.messages

import com.google.gson.annotations.SerializedName

enum WorkerCommands:
  case PREDICT, LOAD, UNLOAD, STATS, DESCRIBE, STREAMPREDICT, STREAMPREDICT2, OIPPREDICT
//object WorkerCommands extends Enumeration {
//  type WorkerCommands = Value
//  val PREDICT, LOAD, UNLOAD, STATS, DESCRIBE, STREAMPREDICT, STREAMPREDICT2, OIPPREDICT = Value
//  private var command = nulldef
//  this (command: String) {
//    this ()
//    this.command = command
//  }
//
//  def getCommand: String = command
//}