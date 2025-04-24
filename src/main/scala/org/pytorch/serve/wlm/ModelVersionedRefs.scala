package org.pytorch.serve.wlm

import org.pytorch.serve.archive.model.ModelVersionNotFoundException
import org.pytorch.serve.http.{ConflictStatusException, InvalidModelVersionException}
import org.pytorch.serve.wlm.Model
import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters.*

object ModelVersionedRefs {
  private val logger = LoggerFactory.getLogger(classOf[ModelVersionedRefs])
}

final class ModelVersionedRefs {
//  this.modelsVersionMap = new ConcurrentHashMap[String, Model]
private var modelsVersionMap: TrieMap[String, Model] = new TrieMap[String, Model]
  private var defaultVersion: String = null

  /**
   * Adds a new version of the Model to the Map if it does not exist Sets this version as the
   * default version of the model which is automatically served on the next request to this model.
   * If it already exists in the map, throws an exception with conflict status
   *
   * @param model     : Model object with all the parameters initialized as desired
   * @param versionId : String version ID from the manifest
   * @return None
   */
  @throws[ModelVersionNotFoundException]
  @throws[ConflictStatusException]
  def addVersionModel(model: Model, versionId: String): Unit = {
    ModelVersionedRefs.logger.debug("Adding new version {} for model {}", versionId, model.getModelName)
    if (versionId == null) throw new InvalidModelVersionException("Model version not found. ")
    if (this.modelsVersionMap.putIfAbsent(versionId, model) != null) throw new ConflictStatusException("Model version " + versionId + " is already registered for model " + model.getModelName)
    if (this.defaultVersion == null) this.setDefaultVersion(versionId)
  }

  /**
   * Returns a String object of the default version of this Model
   *
   * @return String obj of the current default Version
   */
  def getDefaultVersion: String = this.defaultVersion

  /**
   * Sets the default version of the model to the version in arg
   *
   * @param A valid String obj with version to set default
   * @return None
   */
  @throws[ModelVersionNotFoundException]
  def setDefaultVersion(versionId: String): Unit = {
    val model = this.modelsVersionMap.get(versionId)
    if (model == null) throw new ModelVersionNotFoundException("Model version " + versionId + " does not exist for model " + this.getDefaultModel.getModelName)
    ModelVersionedRefs.logger.debug("Setting default version to {} for model {}", versionId, model.get.getModelName)
    this.defaultVersion = versionId
  }

  /**
   * Returns the default Model obj
   *
   * @param None
   * @return On Success - a Model Obj corresponding to the default Model obj On Failure - null
   */
  def getDefaultModel: Model = {
    // TODO should not throw invalid here as it has been already validated??
    this.modelsVersionMap.get(this.defaultVersion).get
  }

  /**
   * Removes the specified version of the model from the Map If it's the default version then
   * throws an exception The Client is responsible for setting a new default prior to deleting the
   * current default
   *
   * @param A String specifying a valid non-default version Id
   * @return On Success - Removed model for given version Id
   * @throws ModelVersionNotFoundException
   * @throws On Failure - throws InvalidModelVersionException and ModelNotFoundException
   */
  @throws[InvalidModelVersionException]
  @throws[ModelVersionNotFoundException]
  def removeVersionModel(versionIdTmp: String): Model = {
    val versionId = if (versionIdTmp == null) then this.getDefaultVersion else versionIdTmp
    if (this.defaultVersion == versionId && modelsVersionMap.size > 1) throw new InvalidModelVersionException(String.format("Can't remove default version: %s", versionId))
    val model = this.modelsVersionMap.remove(versionId)
    if (model == null) throw new ModelVersionNotFoundException(String.format("Model version: %s not found", versionId))
    ModelVersionedRefs.logger.debug("Removed model: {} version: {}", model.get.getModelName, versionId)
    model.get
  }

  /**
   * Returns the Model obj corresponding to the version provided
   *
   * @param A String specifying a valid version Id
   * @return On Success - a Model Obj previously registered On Failure - null
   */
  def getVersionModel(versionId: String): Model = {
    var model: Model = null
    if (versionId != null) model = this.modelsVersionMap.get(versionId).get
    else model = this.getDefaultModel
    model
  }

  // scope for a nice generator pattern impl here
  // TODO what is this for?
  def forAllVersions: Model = null

  def getAllVersions: Map[String, Model] = this.modelsVersionMap.toMap //.entrySet
}