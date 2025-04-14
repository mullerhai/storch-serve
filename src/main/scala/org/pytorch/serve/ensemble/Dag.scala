package org.pytorch.serve.ensemble

import java.util
import scala.jdk.CollectionConverters._
/** Direct acyclic graph for ensemble */
class Dag {
  private val nodes = new util.HashMap[String, Node]
  private val dagMap = new util.HashMap[String, util.Map[String, util.Set[String]]]

  def addNode(node: Node): Unit = {
    if (!checkNodeExist(node)) {
      nodes.put(node.getName, node)
      val degreeMap = new util.HashMap[String, util.Set[String]]
      degreeMap.put("inDegree", new util.HashSet[String])
      degreeMap.put("outDegree", new util.HashSet[String])
      dagMap.put(node.getName, degreeMap)
    }
  }

  def checkNodeExist(node: Node): Boolean = nodes.containsKey(node.getName)

  def hasEdgeTo(from: Node, to: Node): Boolean = dagMap.get(from.getName).get("inDegree").contains(to.getName)

  @throws[InvalidDAGException]
  def addEdge(from: Node, to: Node): Unit = {
    if (!checkNodeExist(from)) addNode(from)
    if (!checkNodeExist(to)) addNode(to)
    if (from.getName == to.getName) throw new InvalidDAGException("Self loop exception")
    if (hasEdgeTo(to, from)) throw new InvalidDAGException("loop exception")
    dagMap.get(from.getName).get("outDegree").add(to.getName)
    dagMap.get(to.getName).get("inDegree").add(from.getName)
  }

  def getEndNodeNames(degree: String): util.Set[String] = {
    val startNodes = new util.HashSet[String]
    
    for (entry <- dagMap.entrySet.asScala) {
      val value = entry.getValue.get(degree)
      if (value.isEmpty) startNodes.add(entry.getKey)
    }
    startNodes
  }

  def getStartNodeNames: util.Set[String] = getEndNodeNames("inDegree")

  def getLeafNodeNames: util.Set[String] = getEndNodeNames("outDegree")

  def getDegreeMap(degree: String): util.Map[String, Integer] = {
    val inDegreeMap = new util.HashMap[String, Integer]
//    import scala.collection.JavaConversions._
    for (entry <- dagMap.entrySet.asScala) {
      inDegreeMap.put(entry.getKey, entry.getValue.get(degree).size)
    }
    inDegreeMap
  }

  def getInDegreeMap: util.Map[String, Integer] = getDegreeMap("inDegree")

  def getOutDegreeMap: util.Map[String, Integer] = getDegreeMap("outDegree")

  def getNodes: util.Map[String, Node] = nodes

  def getDagMap: util.Map[String, util.Map[String, util.Set[String]]] = dagMap

  @throws[InvalidDAGException]
  def validate: util.ArrayList[String] = {
    val startNodes = getStartNodeNames
    if (startNodes.size != 1) throw new InvalidDAGException("DAG should have only one start node")
    val topoSortedList = new util.ArrayList[String]
    val de = new DagExecutor(this)
    de.execute(null, topoSortedList)
    if (topoSortedList.size != nodes.size) throw new InvalidDAGException("Not a valid DAG")
    topoSortedList
  }
}