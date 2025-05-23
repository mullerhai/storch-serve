package org.pytorch.serve.device.interfaces

import org.apache.commons.io.IOUtils
import org.pytorch.serve.device.Accelerator
import org.slf4j.{Logger, LoggerFactory}

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util
import java.util.stream.Collectors
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
/**
 * Provides functionality to detect hardware devices for accelerated workloads. For example GPUs.
 */
object IAcceleratorUtility {
  val logger: Logger = LoggerFactory.getLogger(classOf[IAcceleratorUtility])

  /**
   * Parses a string representation of visible devices into an {@code LinkedHashSet} of device
   * identifiers.
   *
   * <p>This method processes a comma-separated list of device identifiers, typically obtained
   * from an environment variable like {X}_VISIBLE_DEVICES. It performs validation and cleaning of
   * the input string.
   *
   * @param visibleDevices A string containing comma-separated device identifiers. Can be null or
   *                       empty.
   * @return A LinkedHashSet of Integers each representing a device identifier. Returns an empty
   *         set if the input is null or empty.
   * @throws IllegalArgumentException if the input string is not in the correct format (integers
   *                                  separated by commas, with or without spaces).
   * @example // Returns [0, 1, 2] parseVisibleDevicesEnv("0,1,2")
   *          <p>// notice spaces between the commas and the next number // Returns [0, 1, 2]
   *          parseVisibleDevicesEnv("0, 1, 2")
   *          <p>// Returns [0, 2] parseVisibleDevicesEnv("0,0,2")
   *          <p>// Returns [] parseVisibleDevicesEnv("")
   *          <p>// Throws IllegalArgumentException parseVisibleDevicesEnv("0,1,a")
   */
  def parseVisibleDevicesEnv(visibleDevices: String): mutable.LinkedHashSet[Integer] = {
    // return an empty set if null or an empty string is passed
    if (visibleDevices == null || visibleDevices.isEmpty) return new mutable.LinkedHashSet[Integer]
    // Remove all spaces from the input
    val cleaned = visibleDevices.replaceAll("\\s", "")
    // Check if the cleaned string matches the pattern of integers separated by
    // commas
    if (!cleaned.matches("^\\d+(,\\d+)*$")) throw new IllegalArgumentException("Invalid format: The env defining visible devices must be integers separated by commas")
    // split the string on comma, cast to Integer, and collect to a List
    val allIntegers = new ListBuffer[String]()
    allIntegers.addAll(cleaned.split(","))
//    val allIntegers = util.Arrays.stream(cleaned.split(",")).map(Integer.parseInt).collect(Collectors.toList).asScala
    // use Sets to deduplicate integers
val uniqueIntegers = new mutable.LinkedHashSet[Integer]
    val duplicates = allIntegers.map(ele =>Integer.parseInt(ele)).filter(num =>{
      
      if(!uniqueIntegers.contains(num)){
        uniqueIntegers.add(num) 
        true
      } else false
    }).toSet
//    val duplicates = allIntegers.stream.filter((n: Integer) => !uniqueIntegers.add(n)).collect(Collectors.toSet)
    if (!duplicates.isEmpty) logger.warn("Duplicate GPU IDs found in {}: {}. Duplicates will be removed.", visibleDevices, duplicates)
    // return the set of unique integers
    uniqueIntegers
  }

  /**
   * Converts a number of bytes to megabytes.
   *
   * <p>This method uses the binary definition of a megabyte, where 1 MB = 1,048,576 bytes (1024 *
   * 1024). The result is rounded to two decimal places.
   *
   * @param bytes The number of bytes to convert, as a long value.
   * @return The equivalent number of megabytes, as a double value rounded to two decimal places.
   */
  def bytesToMegabytes(bytes: Long): Integer = {
    val BYTES_IN_MEGABYTE = 1024 * 1024
    (bytes / BYTES_IN_MEGABYTE).toInt
  }

  /**
   * Executes an SMI (System Management Interface) command and returns its output.
   *
   * <p>This method runs the specified command using a ProcessBuilder, combines standard output
   * and error streams, waits for the process to complete, and returns the output as a string.
   *
   * @param command An array of strings representing the SMI command and its arguments.
   * @return A string containing the output of the SMI command.
   * @throws AssertionError If the SMI command returns a non-zero exit code.
   * @throws Error          If an IOException or InterruptedException occurs during execution. The original
   *                        exception is wrapped in the Error.
   */
  def callSMI(command: Array[String]): String = try {
    val processBuilder = new ProcessBuilder(command*)
    processBuilder.redirectErrorStream(true)
    val process = processBuilder.start
    val ret = process.waitFor
    if (ret != 0) throw new AssertionError("SMI command returned a non-zero")
    val output = IOUtils.toString(process.getInputStream, StandardCharsets.UTF_8)
    if (output.isEmpty) throw new AssertionError("Unexpected smi response.")
    output
  } catch {
    case e@(_: IOException | _: InterruptedException) =>
      logger.debug("SMI command not available or failed: " + e.getMessage)
      throw new Error(e)
  }
}

trait IAcceleratorUtility {
  /**
   * Returns the name of the environment variable used to specify visible GPU devices.
   * Implementing classes should define this based on their specific requirements.
   *
   * <p>Examples are 'HIP_VISIBLE_DEVICES', 'CUDA_VISIBLE_DEVICES'
   *
   * @return The name of the environment variable for visible GPU devices.
   */
    def getGpuEnvVariableName: String

  /**
   * Returns the SMI command specific to the implementing class.
   *
   * @return An array of strings representing the SMI command and its arguments for getting the
   *         utilizaiton stats for the available accelerators
   */
  def getUtilizationSmiCommand: Array[String]

  /**
   * Parses the output of a system management interface (SMI) command to create a list of {@code
   * Accelerator} objects with updated metrics.
   *
   * @param smiOutput The raw output string from the SMI command.
   * @param parsed_gpu_ids A set of GPU IDs that have already been parsed.
   * @return An {@code ArrayList} of {@code Accelerator} objects representing the parsed
   * accelerators.
   *
   * @implNote The specific SMI command, output format, and environment variables will vary
   *           depending on the accelerator type. The SMI command should return core usage, memory
   *           utilization. Implementations should document these specifics in their method comments. If
   *           {@code parsed_gpu_ids} is empty, all accelerators found by the smi command should be
   *           returned.
   * @throws IllegalArgumentException If the SMI output is invalid or cannot be parsed.
   * @throws NullPointerException     If either {@code smiOutput} or {@code parsed_gpu_ids} is null.
   */
  def smiOutputToUpdatedAccelerators(smiOutput: String, parsed_gpu_ids: mutable.LinkedHashSet[Integer]): List[Accelerator]

  /**
   * @param availableAcceleratorIds
   * @return
   */
  def getAvailableAccelerators(availableAcceleratorIds: mutable.LinkedHashSet[Integer]): List[Accelerator]

  /**
   * Updates the utilization information for a list of accelerators.
   *
   * <p>This method retrieves the current utilization statistics for the given accelerators using
   * a System Management Interface (SMI) command specific to the implementing class. It then
   * parses the SMI output and returns an updated {@code ArrayList} of accelerator objects with
   * the latest information.
   *
   * @param accelerators An ArrayList of Accelerator objects to be updated. Must not be null or
   *                     empty.
   * @return An ArrayList of updated Accelerator objects with the latest utilization information.
   * @throws IllegalArgumentException If the input accelerators list is null or empty, or if the
   *                                  SMI command returned by getUtilizationSmiCommand() is null or empty.
   * @throws RuntimeException         If an error occurs while executing the SMI command or parsing its
   *                                  output. The specific exception will depend on the implementation of callSMI() and
   *                                  smiOutputToAccelerators().
   * @implNote This method uses getUtilizationSmiCommand() to retrieve the SMI command specific to
   *           the implementing class. Subclasses must implement this method to provide the correct
   *           command. The method also relies on callSMI() to execute the command and
   *           smiOutputToAccelerators() to parse the output, both of which must be implemented by the
   *           subclass.
   * @implSpec The implementation first checks if the input is valid, then retrieves and validates
   *           the SMI command. It executes the command, extracts the GPU IDs from the input
   *           accelerators, and uses these to parse the SMI output into updated Accelerator objects.
   * @see #getUtilizationSmiCommand()
   * @see #callSMI(String[])
   * @see #smiOutputToUpdatedAccelerators(String, LinkedHashSet)
   */
  def getUpdatedAcceleratorsUtilization(accelerators: List[Accelerator]): List[Accelerator] = {
    if (accelerators == null || accelerators.isEmpty) {
      IAcceleratorUtility.logger.warn("No accelerators to update.")
      throw new IllegalArgumentException("`accelerators` cannot be null or empty when trying to update the accelerator stats")
    }
    val smiCommand = getUtilizationSmiCommand
    if (smiCommand == null || smiCommand.length == 0) throw new IllegalArgumentException("`smiCommand` cannot be null or empty when trying to update accelerator stats")
    val smiOutput = IAcceleratorUtility.callSMI(smiCommand)
    val acceleratorIds = new mutable.LinkedHashSet[Integer]
    Seq(accelerators).flatten.map((accelerator: Accelerator) => {
      accelerator.id
    }).foreach(el =>acceleratorIds.add(el))
//    val acceleratorIds = accelerators.stream().map((accelerator: Accelerator) => accelerator.id).collect(Collectors.toCollection(new util.LinkedHashSet))
    val updatedAccelerators = smiOutputToUpdatedAccelerators(smiOutput, acceleratorIds)
    updatedAccelerators
  }
}