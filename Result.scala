package ScMaBoSS

import java.io.PrintWriter
import java.io._
import scala.collection.parallel.immutable._
  import scala.collection.immutable._

import ScMaBoSS.CfgMbss
import org.saddle._
import org.nspl
import org.nspl._
import org.nspl.saddle._
import org.nspl.data._
import org.nspl.awtrenderer._
import org.saddle.io._
import org.saddle.io.CsvImplicits._

/** Data sent to MaBoSS server
  *
  * @param network
  * @param config
  * @param command
  */
case class ClientData(network : String = null, config : String = null, command : String = "Run") {}

/** Parameters for MaBoSS server
  *
  * @param check
  * @param hexfloat
  * @param augment
  * @param overRide
  * @param verbose
  */
case class Hints(check : Boolean = false, hexfloat : Boolean = false, augment : Boolean =  true,
                 overRide : Boolean = false , verbose : Boolean = false) {}

/** Parsed results from MaBoSS server
  *
  * @param status
  * @param errmsg
  * @param stat_dist
  * @param prob_traj
  * @param traj
  * @param FP
  * @param runlog
  */
case class ResultData(status : Int = 0, errmsg : String = "" , stat_dist : String = null,
                      prob_traj : String = null, traj : String = null, FP : String = null, runlog : String = null) {}

/** Companion object for updating probtraj line for UPMaBoSS and constructor from MaBoSS run
  *
  */
object Result {
  /** initial condition and normalization factor from last line of probtraj, usefull for UPMaBoSS
    *
    * @param line
    * @param divNode
    * @param deathNode
    * @return
    */
  def updateLine(line : String,divNode: String, deathNode: String,verbose:Boolean = false): (List[(String, Double)], Double) = {
    val nonNormDist = line.split("\t").dropWhile("^[0-9]".r.findFirstIn(_).isDefined).
      sliding(3, 3).map(x => (x(0), x(1).toDouble)).
      filter(x => !x._1.split(" -- ").contains(deathNode)).
      map(x => {
        if (x._1.split(" -- ").contains(divNode)) {
          x._1.split(" -- ").filter(x => x != divNode).toList match {
            case Nil => ("<nil>",x._2*2)
            case l => (l.mkString(" -- "),x._2*2)
          }
        } else (x._1 , x._2)
      }).toList.
      groupBy(_._1.split(" -- ").toSet).map(x=> (x._1,x._2.map(_._2).sum)).toList.map(x=>(x._1.mkString(" -- "),x._2)) // group states
    val normFactor = nonNormDist.map(x => x._2).sum
    if (verbose) println("Norm. factor: "+normFactor)
    (nonNormDist.map(x => (x._1, x._2 / normFactor)), normFactor)
  }

  /** for overloaded Constructor from MaBoSS server run
    *
    * @param mbcli
    * @param simulation
    * @param hints
    * @return
    */
  //def fromInputsMBSS(mbcli : MaBoSSClient, simulation : CfgMbss, hints : Hints) : Result = {
  private def fromInputsMBSS(mbcli : MaBoSSClient, simulation : CfgMbss, hints : Hints) : String = {
    val command: String = if (hints.check) {
      GlCst.CHECK_COMMAND
    } else GlCst.RUN_COMMAND
    val clientData: ClientData = ClientData(simulation.bndMbss.bnd, simulation.cfg, command)
    val data: String = DataStreamer.buildStreamData(clientData, hints)
    val outputData: String = mbcli.send(data)
    mbcli.close()
    outputData
    //new Result(simulation,hints.verbose,hints.hexfloat,outputData)
  }

  /** Boolean state probability trajectory, given a list of probtraj and a network state
    *
    * @param netState
    * @param probTrajLines
    * @return probability over time
    */
  def stateTrajectory(netState: NetState,probTrajLines : List[String]): List[(Double, Double)] = { // to be tested, the .isDefined
    val activeNodes = netState.state.filter(nodeBool => nodeBool._2).keySet
    val unactiveNodes = netState.state.filter(nodeBool => !nodeBool._2).keySet
    probTrajLines.map(probTrajLine => {
      val splitProbTrajLine = probTrajLine.split("\t")
      val stateDistProb: List[(String, Double)] = splitProbTrajLine.dropWhile("^[0-9].*".r.findFirstIn(_).isDefined).
        sliding(3, 3).map(x => (x(0), x(1).toDouble)).toList
      val prob = stateDistProb.filter(stateProb => {
        val nodes = stateProb._1.split(" -- ").toSet
        activeNodes.diff(nodes).isEmpty & unactiveNodes.intersect(nodes).isEmpty}).
        map(_._2).sum
      (splitProbTrajLine.toList.head.toDouble, prob)
    })
  }

  /** Node state probability trajectory, given a list of probtraj and a node
    *
    * @param node
    * @param probTrajLines
    * @return probability over time
    */
  def nodeTrajectory(node: String,probTrajLines : List[String]): List[(Double, Double)] = // to be tested, the .isDefined
  {
    probTrajLines.map(probTrajLine => {
      val splitProbTrajLine = probTrajLine.split("\t")
      (splitProbTrajLine.head.toDouble,
        splitProbTrajLine.dropWhile("^[0-9].*".r.findFirstIn(_).isDefined).
          sliding(3, 3).map(x => (x(0), x(1).toDouble)).filter(_._1.split(" -- ").contains(node)).map(_._2).sum)
    })
  }

  /** Plot Boolean state probability trajectories, given a list of probtraj and a list of network states
    *
    * @param netStates
    * @param probTrajLines
    * @param filename
    * @return
    */
  def plotStateTraj(netStates : List[NetState],probTrajLines : List[String],filename : String) : File = {
    val listTraj =netStates.map(x => stateTrajectory(x,probTrajLines))
    val Mat4Plot : Mat[Double]= Mat((Vec(listTraj.head.map(_._1).toArray) :: //matrix with x coordinates
      listTraj.map(x => Vec(x.map( y=> y._2).toArray)) ::: // y coordinates
      (1 to netStates.length).map(x=>Vec(List.fill(listTraj.head.length)(x.toDouble-1).toArray)).toList).toArray) //and colors
    val builtElement =
      xyplot(Mat4Plot -> (
        (1 to netStates.length).map(x => line(xCol = 0,yCol = x,colorCol = netStates.length+x, // lines given the column indices of the matrix
          color = DiscreteColors(netStates.length - 1))).toList  :::
          (1 to netStates.length).map(x => // points given the column indices of the matrix
            point(xCol = 0,yCol = x, colorCol = netStates.length+x, sizeCol=3+2*netStates.length,
              shapeCol=3+2*netStates.length, errorBottomCol =1+netStates.length , errorTopCol = 1+netStates.length , size = 4d,
              color = DiscreteColors(netStates.length-1))).toList ) // careful, need to add zero to errorTop/Bottom
      )(xlab = "Time",ylab="Probability",extraLegend =
        netStates.zipWithIndex.map(x => x._1.toString -> PointLegend(shape = Shape.rectangle(0, 0, 1, 1),
          color = DiscreteColors(netStates.length-1)(x._2.toDouble) )),ylim = Some(0,1),xWidth = RelFontSize(40d))
    val pdfFile = new File(filename)
    pdfToFile(pdfFile,sequence(builtElement :: Nil,FreeLayout).build)
  }
}

/** Results of MaBoSS server
  *
  * @param simulation configuration and network
  * @param verbose flag for parser of data
  * @param hexfloat flag for writing data of file
  * @param outputData raw data from MaBoSS server
  */
class Result(val simulation : CfgMbss, verbose : Boolean,hexfloat : Boolean,outputData : String) {

  /** Constructor of Result from MaBoSS server run
    *
    * @param mbcli
    * @param simulation
    * @param hints
    * @return
    */
  def this(mbcli : MaBoSSClient, simulation : CfgMbss, hints : Hints) {
    this(simulation,hints.verbose,hints.hexfloat,Result.fromInputsMBSS(mbcli : MaBoSSClient, simulation : CfgMbss, hints : Hints))}
  /**Generates String or hexString from Double, according to hexfloat
    *
    * @param double
    * @return
    */
  def doubleToMbssString(double: Double): String = { // not yet used for writing files
    if (hexfloat) java.lang.Double.toHexString(double) else double.toString
  }
  // val command: String = if (hints.check) {
  //  GlCst.CHECK_COMMAND
  //} else GlCst.RUN_COMMAND
  //val clientData: ClientData = ClientData(simulation.bndMbss.bnd, simulation.cfg, command)
  //val data: String = DataStreamer.buildStreamData(clientData, hints)
  //val outputData: String = mbcli.send(data)
  val parsedResultData: ResultData = DataStreamer.parseStreamData(outputData, verbose)

  /** updates last probability distribution for UPMaBoSS
    *
    * @param divNode   division node
    * @param deathNode death node
    * @return (new_statistical_distribution,normalization_factor)
    */
  def updateLastLine(divNode: String, deathNode: String,verbose:Boolean = false): (List[(String, Double)], Double) = { // to be tested
    Result.updateLine(parsedResultData.prob_traj.split("\n").toList.last,divNode,deathNode,verbose)
  }

  def writeProbTraj2File(filename: String): Unit = {
    val pw = new PrintWriter(new File(filename))
    pw.write(parsedResultData.prob_traj)
    pw.close()
  }

  def writeFP2File(filename: String): Unit = {
    val pw = new PrintWriter(new File(filename))
    pw.write(parsedResultData.FP)
    pw.close()
  }

  def writeStatDist2File(filename: String): Unit = {
    val pw = new PrintWriter(new File(filename))
    pw.write(parsedResultData.stat_dist)
    pw.close()
  }

  /**  Boolean state probability trajectory, given a network state
    *
    * @param netState
    * @return
    */
  def stateTrajectory(netState: NetState): List[(Double, Double)] = { // to be tested, the .isDefined
    Result.stateTrajectory(netState,parsedResultData.prob_traj.split("\n").toList.tail)
  }

  /** Node state probability trajectory, given a node
    *
    * @param node
    * @return
    */
  def nodeTrajectory(node: String): List[(Double, Double)] = // to be tested, the .isDefined
  {
    Result.nodeTrajectory(node,parsedResultData.prob_traj.split("\n").toList.tail)
  }

  /** Plot Boolean state probability trajectories, given a list of network states
    *
    * @param netStates
    * @param filename
    * @return
    */
  def plotStateTraj(netStates : List[NetState],filename : String) : File = {
    Result.plotStateTraj(netStates,parsedResultData.prob_traj.split("\n").toList.tail,filename)
  }
}

/** Trait for parallel runs of MaBoSS with reduction
  *
  * @tparam OutType
  */
trait ParReducibleRun[OutType] {
  def linCombine(o1:OutType,o2:OutType) : OutType
  def multiply(o: OutType,d:Double) : OutType
  def generate(r:Result) : OutType

  /** parallel runs
    *
    * @param cfgMbss
    * @param hints
    * @param seedHostPortSet parralel set containing seed and (port,host) for MaBoSS servers
    * @return
    */
  def apply(cfgMbss : CfgMbss,hints : Hints,seedHostPortSet : ParSet[(Int,String,Int)]) : OutType = {
    multiply(
      seedHostPortSet.map(seedHostPort => {
      val newCfg = cfgMbss.update((("seed_pseudorandom",seedHostPort._1.toString) :: Nil).toMap)
      val mbcli = new MaBoSSClient(seedHostPort._2,seedHostPort._3)
      val result = mbcli.run(newCfg,hints)
      mbcli.close()
      generate(result)
    }).reduce((x,y) => linCombine(x,y)),1/seedHostPortSet.size.toDouble)
  }
}

/** Trait for parallel runs of MaBoSS, outputs being a probability distribution
  *
  */
trait ParReducibleProbDist extends ParReducibleRun[Map[NetState,Double]] {
  def linCombine(fpMap1: Map[NetState, Double], fpMap2: Map[NetState, Double]): Map[NetState, Double] = {
    (fpMap1.toList ::: fpMap2.toList).groupBy(_._1).map(x => (x._1, x._2.map(_._2).sum))
  }
  def multiply(fpMap: Map[NetState, Double], d: Double): Map[NetState, Double] = {
    fpMap.map(x => (x._1, x._2 * d))
  }
}

/** Concrete application of ParReducible run for fixed point distribution
  *
  */
object ParReducibleFP extends ParReducibleProbDist
{
  def generate(r:Result) : Map[NetState,Double] = r.parsedResultData.FP.split("\n").tail.tail.
    map(line => {val lSplit = line.split("\t");(new NetState(lSplit(1),r.simulation),lSplit(0).toDouble)}).toMap
}

/** parallel runs with FP distribution output
  *
  * @param fp
  */
private class ParReducibleFP(val fp : Map[NetState,Double]) {
  def this(cfgMbss : CfgMbss,hints : Hints,seedHostPortSet : ParSet[(Int,String,Int)]) =
    this(ParReducibleFP(cfgMbss,hints,seedHostPortSet))
}

/** Concrete application of ParReducible run for last probability distribution
  *
  */
object ParReducibleLastLine extends ParReducibleProbDist
{
  def generate(r:Result) : Map[NetState,Double] = {
    r.parsedResultData.prob_traj.split("\n").last.split("\t").
    dropWhile("^[0-9].*".r.findFirstIn(_).isDefined).sliding(3, 3).
      map(x => (new NetState(x(0),r.simulation), x(1).toDouble)).toMap
  }
}

/** parallel runs with last line probability distribution output
  *
  * @param fp
  */
private class ParReducibleLastLine(val fp : Map[NetState,Double]) {
  def this(cfgMbss : CfgMbss,hints : Hints,seedHostPortSet : ParSet[(Int,String,Int)]) =
    this(ParReducibleLastLine(cfgMbss,hints,seedHostPortSet))
}

