package org.pytorch.serve.ensemble

import java.util
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
/** Direct acyclic graph for ensemble */
class Dag {
  private val nodes = new mutable.HashMap[String, Node]
  private val dagMap = new mutable.HashMap[String, mutable.Map[String, mutable.HashSet[String]]]

  def addNode(node: Node): Unit = {
    if (!checkNodeExist(node)) {
      nodes.put(node.getName, node)
      val degreeMap = new mutable.HashMap[String, mutable.HashSet[String]]
      degreeMap.put("inDegree", new mutable.HashSet[String])
      degreeMap.put("outDegree", new mutable.HashSet[String])
      dagMap.put(node.getName, degreeMap)
    }
  }

  def checkNodeExist(node: Node): Boolean = nodes.contains(node.getName)

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

  def getLeafNodeNames: mutable.HashSet[String] = getEndNodeNames("outDegree")

  def getInDegreeMap: mutable.Map[String, Integer] = getDegreeMap("inDegree")

  def getDegreeMap(degree: String): mutable.Map[String, Integer] = {
    val inDegreeMap = new mutable.HashMap[String, Integer]
//    import scala.collection.JavaConversions._
    for (entry <- dagMap.toSeq) {
      inDegreeMap.put(entry._1, entry._2.get(degree).size)
    }
    inDegreeMap
  }

  def getOutDegreeMap: mutable.Map[String, Integer] = getDegreeMap("outDegree")

  def getNodes: Map[String, Node] = nodes.toMap

  def getDagMap: Map[String, Map[String, Set[String]]] = dagMap.map((k, v) => (k, v.map((h, j) => (h, j.toSet)).toMap)).toMap

  @throws[InvalidDAGException]
  def validate: List[String] = {
    val startNodes = getStartNodeNames
    if (startNodes.size != 1) throw new InvalidDAGException("DAG should have only one start node")
    val topoSortedList = new ListBuffer[String]
    val de = new DagExecutor(this)
    de.execute(null, topoSortedList)
    if (topoSortedList.size != nodes.size) throw new InvalidDAGException("Not a valid DAG")
    topoSortedList.toList
  }

  def getStartNodeNames: mutable.HashSet[String] = getEndNodeNames("inDegree")

  def getEndNodeNames(degree: String): mutable.HashSet[String] = {
    val startNodes = new mutable.HashSet[String]

    for (entry <- dagMap.toSeq) {
      val value = entry._2.get(degree)
      if (value.isEmpty) startNodes.add(entry._1)
    }
    startNodes
  }
}