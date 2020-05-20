package ScMaBoSS

import java.io.PrintWriter
import java.io._
import scala.collection.parallel.immutable._
import scala.collection.immutable._

import org.saddle._
import org.nspl._
import org.nspl.saddle._
import org.nspl.awtrenderer._

/** Data sent to MaBoSS server, need to be transformed by DataStreamer.buildStreamData
  *
  * @param network bnd in String
  * @param config cfg in String
  * @param command command sent to server
  */
case class ClientData(network : String = null, config: String = null, command: String = "Run") {}

/** Parameters for MaBoSS server
  *
  * @param check if true, just check coherence of network and config
  * @param hexfloat if true, double a represented in hexfloat format
  * @param augment server parameter
  * @param overRide server paramteer
  * @param verbose server parameter
  */
case class Hints(check: Boolean = false, hexfloat: Boolean = false, augment: Boolean =  true,
                 overRide: Boolean = false , verbose: Boolean = false) {}

/** Parsed results from MaBoSS server
  *
  * @param status status
  * @param errmsg error message
  * @param stat_dist stationary distribution estimates
  * @param prob_traj trajectory of probabilities
  * @param traj Markov process trajectories
  * @param FP fixed points
  * @param runlog log of run
  */
case class ResultData(status: Int = 0, errmsg: String = "" , stat_dist: String = null,
                      prob_traj: String = null, traj: String = null, FP: String = null, runlog: String = null) {}

/** Companion object for updating probtraj line for UPMaBoSS and constructor from MaBoSS run
  *
  */
object Result {
  /** initial condition and normalization factor from a line of probtraj, useful for UPMaBoSS
    * Not private because UPStepLight of UPMaBoSS uses it
    *
    * @param line line of probtraj
    * @param divNode division node
    * @param deathNode death node
    * @return
    */
  def updateLine(line: String,divNode: String, deathNode: String,verbose:Boolean = false): (List[(String, Double)], Double) = {
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

  /** for Constructor from MaBoSS server run with option
    *
    * @param mbcli MaBoSS (queuing) client
    * @param simulation cfg with associated bnd
    * @param hints hints for server
    * @param jobName used only if mbcli is an intermediate queuing soket server
    * @return
    */
  def fromInputsMBSS(mbcli : MaBoSSClient, simulation : CfgMbss, hints : Hints,jobName : String = "") : Option[Result] = {
    val command: String = if (hints.check) {
      GlCst.CHECK_COMMAND
    } else GlCst.RUN_COMMAND
    val clientData: ClientData = ClientData(simulation.bndMbss.bnd, simulation.cfg, command)
    val data: String = mbcli match  {
      case _ : MaBoSSQuClient   => jobName + "\n" + DataStreamer.buildStreamData(clientData, hints)
      case _ => DataStreamer.buildStreamData(clientData, hints)
    }
          mbcli.send(data) match {
        case Some(s) => Some(new Result(simulation,hints.verbose,hints.hexfloat,s))
        case None => None
      }
  }
 }

/** Results of MaBoSS server
  *
  * @param simulation configuration and network
  * @param verbose flag for parser of data
  * @param hexfloat flag for writing data of file
  * @param outputData raw data from MaBoSS server
  */
class Result(val simulation : CfgMbss, verbose : Boolean,hexfloat : Boolean,outputData : String) extends ResultProcessing {

  ///** Constructor of Result from MaBoSS server run. Socket is closed after the run.
  //  *
  //  * @param mbcli
  //  * @param simulation
  //  * @param hints
  //  * @return
  //  */
  //def this(mbcli : MaBoSSClient, simulation : CfgMbss, hints : Hints) {
    //this(simulation,hints.verbose,hints.hexfloat,Result.fromInputsMBSS(mbcli : MaBoSSClient, simulation : CfgMbss, hints : Hints))}
  /**Generates String or hexString from Double, according to hexfloat
    *
    * @param double input double
    * @return
    */
  def doubleToMbssString(double: Double): String = { // not yet used for writing files
    if (hexfloat) java.lang.Double.toHexString(double) else double.toString
  }

  val parsedResultData: ResultData = DataStreamer.parseStreamData(outputData, verbose)
  val linesWithTime : List[String] = parsedResultData.prob_traj.split("\n").toList.tail
  val sizes : List[Double] = List.fill(linesWithTime.length)(1.0)
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

  /** Distribution from given probtraj line index
    *
    * @param index index of line
    * @return
    */
  def probTrajLine2Dist(index: Int): Array[(NetState, Double)] = super.probTrajLine2Dist(index, simulation)
}

/** Trait for parallel runs of MaBoSS with reduction
  *
  * @tparam OutType generic of output
  */
trait ParReducibleRun[OutType] {
  /** Linear combination of two OutType
    *
    * @param o1 left member
    * @param o2 right member
    * @return
    */
  protected def linCombine(o1: OutType, o2: OutType): OutType

  /** multiplication of an OutType with a real number for normalization
    *
    * @param o output
    * @param d real number
    * @return
    */
  protected def multiply(o: OutType, d: Double): OutType

  /** Generation of an OutType from a MaBoSS Result
    *
    * @param r Result
    * @return
    */
  protected def generate(r: Result): OutType

  /** parallel runs
    *
    * @param cfgMbss input of simulation
    * @param hints hint for server
    * @param seedHostPortSet parallel set containing seed and (port,host) for MaBoSS servers
    * @return
    */
  def apply(cfgMbss: CfgMbss, hints: Hints, seedHostPortSet: ParSet[(Int, String, Int)]): Option[OutType] = {
    val pSetOptRes: ParSet[OutType] =
      seedHostPortSet.flatMap(seedHostPort => { //flatMap reduces the collection of Option[OutType] in collection of OutType
        MaBoSSClient(seedHostPort._2, seedHostPort._3) match {
          case None => None: Option[OutType]
          case Some(mbcli) => {
            val newCfg = cfgMbss.update((("seed_pseudorandom", seedHostPort._1.toString) :: Nil).toMap)
            val oResult = mbcli.run(newCfg, hints)
            mbcli.close()
            oResult match {
              case Some(result) => {
                println("Done for seed: " + seedHostPort._1 + ", host: " + seedHostPort._2 + ", port: " + seedHostPort._3)
                Some(generate(result))
              }
              case None => None
            }
          }
        }
      })
    if (pSetOptRes.isEmpty) None else {
      Some(
        multiply(pSetOptRes.reduce((x, y) => linCombine(x, y)), 1 / pSetOptRes.size.toDouble))
    }
  }
}
/** Trait for parallel runs of MaBoSS, outputs being a probability distribution
  *
  */
trait ParReducibleProbDist extends ParReducibleRun[Map[NetState,Double]] {
  protected def linCombine(fpMap1: Map[NetState, Double], fpMap2: Map[NetState, Double]): Map[NetState, Double] = {
    (fpMap1.toList ::: fpMap2.toList).groupBy(_._1).map(x => (x._1, x._2.map(_._2).sum))
  }
  protected def multiply(fpMap: Map[NetState, Double], d: Double): Map[NetState, Double] = {
    fpMap.map(x => (x._1, x._2 * d))
  }
}

/** Concrete application of ParReducible run for fixed point distribution
  *
  */
object ParReducibleFP extends ParReducibleProbDist
{
  protected def generate(r:Result) : Map[NetState,Double] = r.parsedResultData.FP.split("\n").tail.tail.
    map(line => {val lSplit = line.split("\t");(new NetState(lSplit(1),r.simulation),lSplit(0).toDouble)}).toMap
}

/** Concrete application of ParReducible run for last probability distribution
  *
  */
object ParReducibleLastLine extends ParReducibleProbDist
{
  /** Generate probability distribution from last line of probtraj
    *
    * @param r Result
    * @return
    */
  protected def generate(r:Result) : Map[NetState,Double] = {
    r.parsedResultData.prob_traj.split("\n").last.split("\t").
    dropWhile("^[0-9].*".r.findFirstIn(_).isDefined).sliding(3, 3).
      map(x => (new NetState(x(0),r.simulation), x(1).toDouble)).toMap
  }
}


trait ResultProcessing {

  def linesWithTime : List[String]

  def sizes : List[Double] // carfull: concrete class need to have same length with linesWithTime

  /** Boolean state probability trajectory, given a network state
    *
    * @param netState network state
    * @param normWithSize if true probabilities are multiplied by sizes
    * @return probability over time
    */
  def stateTrajectory(netState: NetState,normWithSize : Boolean = false): List[(Double, Double)] = { // to be tested, the .isDefined
    val activeNodes = netState.state.filter(nodeBool => nodeBool._2).keySet
    val unactiveNodes = netState.state.filter(nodeBool => !nodeBool._2).keySet
    val stateTrajList = linesWithTime.map(probTrajLine => {
      val splitProbTrajLine = probTrajLine.split("\t")
      val stateDistProb: List[(String, Double)] = splitProbTrajLine.dropWhile("^[0-9].*".r.findFirstIn(_).isDefined).
        sliding(3, 3).map(x => (x(0), x(1).toDouble)).toList
      val prob = stateDistProb.filter(stateProb => {
        val nodes = stateProb._1.split(" -- ").toSet
        activeNodes.diff(nodes).isEmpty & unactiveNodes.intersect(nodes).isEmpty}).
        map(_._2).sum
      (splitProbTrajLine.toList.head.toDouble, prob)
    })
    if (normWithSize) stateTrajList.zip(sizes).map(x=>(x._1._1,x._1._2*x._2))
    else stateTrajList
  }

  /** Node state probability trajectory, given a node
    *
    * @param node network node
    * @param normWithSize if true probabilities are multiplied by sizes
    * @return probability over time
    */
  def nodeTrajectory(node: String,normWithSize : Boolean = false): List[(Double, Double)] = // to be tested, the .isDefined
  {
    val nodeTrajList = linesWithTime.map(probTrajLine => {
      val splitProbTrajLine = probTrajLine.split("\t")
      (splitProbTrajLine.head.toDouble,
        splitProbTrajLine.dropWhile("^[0-9].*".r.findFirstIn(_).isDefined).
          sliding(3, 3).map(x => (x(0), x(1).toDouble)).filter(_._1.split(" -- ").contains(node)).map(_._2).sum)
    })
    if (normWithSize) nodeTrajList.zip(sizes).map(x=>(x._1._1,x._1._2*x._2))
    else nodeTrajList
  }

  /** Plot Boolean state probability trajectories, given a list of probtraj and a list of network states
    *
    * @param netStates network state
    * @param firstLast first (start at 1) and last elements to take in the trajectory
    * @param normWithSize if true probabilities are multiplied by sizes
    * @param filename file name
    * @return
    */
  def plotStateTraj(netStates : List[NetState],
                    firstLast : (Int,Int) = (1,linesWithTime.length),
                    normWithSize : Boolean = false,filename : String) : File = {
    val yLim = if (normWithSize) None else Some(0.0,1.0)
    val yLab = if (normWithSize) "Rel. Size" else "Probability"
    val listTraj = netStates.map(x => stateTrajectory(x,normWithSize).slice(firstLast._1-1,firstLast._2))
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
      )(xlab = "Time",ylab=yLab,extraLegend =
        netStates.zipWithIndex.map(x => x._1.toString -> PointLegend(shape = Shape.rectangle(0, 0, 1, 1),
          color = DiscreteColors(netStates.length-1)(x._2.toDouble) )),ylim = yLim,xWidth = RelFontSize(40d))
    val pdfFile = new File(filename)
    pdfToFile(pdfFile,sequence(builtElement :: Nil,FreeLayout).build)
  }

  /** Write tab-separated file of state probability trajectory
    *
    * @param netStates network state
    * @param filename file name
    */
  def writeStateTraj(netStates: List[NetState],filename : String): Unit = {
    val pw = new PrintWriter(new File(filename))
    val header = "Time\t"+netStates.map(x=>x.toString).mkString("\t")+"\n"
    pw.write(header)
    val listTraj =netStates.map(x => stateTrajectory(x


    ))
    val timeList : Vector[Double] = listTraj.head.map(_._1).toVector
    val flatVectorProb : Vector[Double] = listTraj.flatten.map(_._2).toVector
    linesWithTime.indices.foreach(lineIndex =>
    {
      pw.write(timeList(lineIndex).toString)
      netStates.indices.foreach(stateIndex =>
        pw.write("\t" + flatVectorProb(stateIndex *linesWithTime.length  +lineIndex)).toString)
      pw.write("\n")
    })
    pw.close()
  }

  /** Distribution from given probtraj line index
    *
    * @param index line index (start at 0)
    * @param simulation simulation for output nodes that define network states
    * @return
    */
  def probTrajLine2Dist(index: Int,simulation: CfgMbss): Array[(NetState,Double)]  = {
    linesWithTime(index).split("\t").
    dropWhile("^[0-9].*".r.findFirstIn(_).isDefined).sliding(3, 3).
      map(x => (new NetState(x(0),simulation), x(1).toDouble)).toArray
  }
}