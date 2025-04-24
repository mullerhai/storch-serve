package org.pytorch.serve.device.interfaces

import org.pytorch.serve.device.Accelerator
import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.function.Function
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
object ICsvSmiParser {
  val csvSmiParserLogger: Logger = LoggerFactory.getLogger(classOf[ICsvSmiParser])
}

trait ICsvSmiParser {
  /**
   * Parses CSV output from SMI commands and converts it into a list of Accelerator objects.
   *
   * @param csvOutput            The CSV string output from an SMI command.
   * @param parsedAcceleratorIds A set of accelerator IDs to consider. If empty, all accelerators
   *                             are included.
   * @param parseFunction        A function that takes an array of CSV fields and returns an Accelerator
   *                             object. This function should handle the specific parsing logic for different SMI command
   *                             outputs.
   * @return An ArrayList of Accelerator objects parsed from the CSV output.
   * @throws NumberFormatException If there's an error parsing numeric fields in the CSV.
   *                               <p>This method provides a general way to parse CSV output from various SMI commands. It
   *                               skips the header line of the CSV, then applies the provided parseFunction to each
   *                               subsequent line. Accelerators are only included if their ID is in parsedAcceleratorIds,
   *                               or if parsedAcceleratorIds is empty (indicating all accelerators should be included).
   *                               <p>The parseFunction parameter allows for flexibility in handling different CSV formats
   *                               from various SMI commands. This function should handle the specific logic for creating an
   *                               Accelerator object from a line of CSV data.
   */
  def csvSmiOutputToAccelerators(csvOutput: String, parsedGpuIds: mutable.LinkedHashSet[Integer], parseFunction: Function[Array[String], Accelerator]): List[Accelerator] = {
      val accelerators = new ListBuffer[Accelerator]
      val lines: Array[String] = csvOutput.split("\n") //util.Arrays.asList(csvOutput.split("\n")).asScala.toArray
      val addAll = parsedGpuIds.isEmpty
      lines.toSeq.drop(1).foreach(line => {
        val parts = line.split(",")
        try {
          val accelerator = parseFunction.apply(parts)
          if (accelerator != null && (addAll || parsedGpuIds.contains(accelerator.getAcceleratorId))) then accelerators.append(accelerator)
        } catch {
          case e: NumberFormatException =>
            ICsvSmiParser.csvSmiParserLogger.warn("Failed to parse GPU ID: " + parts(1).trim, e)
        }
        
      })
      accelerators.toList
//      csvOutput.lines.skip(1).forEach((line: String) => {
//        val parts = line.split(",")
//        try {
//          val accelerator = parseFunction.apply(parts)
//          if (accelerator != null && (addAll || parsedGpuIds.contains(accelerator.getAcceleratorId))) then accelerators.append(accelerator)
//        } catch {
//          case e: NumberFormatException =>
//            ICsvSmiParser.csvSmiParserLogger.warn("Failed to parse GPU ID: " + parts(1).trim, e)
//        }
//      })
//      lines.stream.skip(1) // Skip the header line.forEach((line: String) => {
//      val parts = line.split(",")
//      try {
//        val accelerator = parseFunction.apply(parts)
//        if (accelerator != null && (addAll || parsedGpuIds.contains(accelerator.getAcceleratorId))) then accelerators.add(accelerator)
//      } catch {
//        case e: NumberFormatException =>
//          ICsvSmiParser.csvSmiParserLogger.warn("Failed to parse GPU ID: " + parts(1).trim, e)
//      }
    
    }

  //)

//}
}