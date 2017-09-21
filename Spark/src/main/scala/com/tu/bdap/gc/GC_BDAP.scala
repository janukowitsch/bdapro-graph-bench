package com.tu.bdap.gc

import org.apache.spark.{ SparkConf, SparkContext }
import org.apache.spark.graphx.{ Edge, Graph }
import org.apache.spark.graphx._
import Math._

import scala.util.Random
import com.tu.bdap.utils.DataSetLoader

/**
 * Created by simon on 21.08.17..
 */
object GC_BDAP {
  def main(args: Array[String]): Unit = {

    //Start the Spark context
    val conf = new SparkConf()
      .setAppName("GC")
    //.setMaster("local")

    val sc = new SparkContext(conf)

    //Set NumIterations
    val numIterations = 10

    //Check input arguments
    if (args.length < 2) {
      System.err.println("Invalid input arguments")
      return
    }

    //Set input Arguments
    val dataSetPath = args(0)
    val dataSet = args(1).toInt

    //Load DataSet
    var edges = dataSet match {
      case 1 => DataSetLoader.loadUSA(sc, dataSetPath)
      case 2 => DataSetLoader.loadTwitter(sc, dataSetPath)
      case 3 => DataSetLoader.loadFriendster(sc, dataSetPath)
      case _ => null
    }
    //Check if DataSet could be loaded
    if (edges == null) {
      System.err.println("Could not load DataSet")
      return
    }

    //Make the Graph undirected
    edges = edges.flatMap(edge => Seq(Edge(edge.srcId, edge.dstId, 0), Edge(edge.dstId, edge.srcId, 0)))

   //Create Graph from edges
    var graph = Graph.fromEdges(edges, 0)

    val degrees = graph.outDegrees
    var init = graph.outerJoinVertices(degrees) { (_, _, optDegree) =>
      optDegree.getOrElse(1)
    }

    val gc = init.mapVertices((id, value) => {
      (-1, -1, value)
    })

    val result = gc.pregel[(VertexId, Int)](initialMsg, numIterations, EdgeDirection.Out)(compute, sendMsg, mergeMsg)
    result.vertices.collect

  }
  val initialMsg = (Long.MaxValue, 0)

  def compute(id: VertexId, value: (Int, Int, Int), message: (Long, Int)): (Int, Int, Int) = {

    val r = Random
    val neighbours = value._3 - message._2
    if (value._2 == 1) {
      return (value._1, value._2, neighbours)
    }
    // resolve conflict of marked nodes, select the one with smaller id
    if (value._2 == 0) {
      if (message._1 > id) {
        return (value._1 + 1, 1, neighbours)
      }
    }
    if (neighbours == 0) {
      return (value._1 + 1, 1, neighbours)
    }
    if (r.nextFloat() <= 1./(2 * neighbours)) {
      return (value._1 + 1, 0, neighbours)
    }
    return (value._1 + 1, -1, neighbours)
  }

  def sendMsg(triplet: EdgeTriplet[(Int, Int, Int), Long]): Iterator[(Long, (Long, Int))] = {
    if (triplet.dstAttr._2 == 1) {
      return Iterator.empty
    }
    if (triplet.srcAttr._2 == 0) {
      return Iterator((triplet.dstId, (triplet.srcId, 0)))
    }
    if (triplet.srcAttr._2 == 1) {
      return Iterator((triplet.dstId, (Long.MaxValue, 1)))
    }
    if (triplet.srcAttr._2 == -1) {
      return Iterator((triplet.dstId, (Long.MaxValue, 0)))
    }
    Iterator.empty
  }

  def mergeMsg(msg1: (Long, Int), msg2: (Long, Int)): (Long, Int) = {
    (min(msg1._1, msg2._1), msg1._2 + msg2._2)
  }

}
