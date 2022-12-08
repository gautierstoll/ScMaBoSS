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
  * @param config  cfg in String
  * @param command command sent to server
  */
case class ClientData(network: String = null, config: String = null, command: String = "Run") {}

/** Parameters for MaBoSS server, <i>necessary for running MaBoSS.</i>
  *
  * @param check    if true, just check coherence of network and config
  * @param hexfloat if true, double a represented in hexfloat format
  * @param augment  server parameter
  * @param overRide server parameter
  * @param verbose  server parameter
  */
case class Hints(check: Boolean = false, hexfloat: Boolean = false, augment: Boolean = true,
                 overRide: Boolean = false, verbose: Boolean = false) {}

/** Parsed results from MaBoSS server
  *
  * @param status    status
  * @param errmsg    error message
  * @param stat_dist stationary distribution estimates
  * @param prob_traj trajectory of probabilities
  * @param traj      Markov process trajectories
  * @param FP        fixed points
  * @param runlog    log of run
  */
case class ResultData(status: Int = 0, errmsg: String = "", stat_dist: String = null,
                      prob_traj: String = null, traj: String = null, FP: String = null, runlog: String = null) {}

/** Companion object for constructor from MaBoSS run
  *
  */
object Result {

  /** for Constructor from MaBoSS server run with option
    *
    * @param mbcli      MaBoSS (queuing) client
    * @param simulation cfg with associated bnd
    * @param hints      hints for server
    * @param jobName    used only if mbcli is an intermediate queuing socket server
    * @return
    */
  def fromInputsMBSS(mbcli: MaBoSSClient, simulation: CfgMbss, hints: Hints, jobName: String = ""): Option[Result] = {
    val command: String = if (hints.check) {
      GlCst.CHECK_COMMAND
    } else GlCst.RUN_COMMAND
    val clientData: ClientData = ClientData(simulation.bndMbss.bnd, simulation.cfg, command)
    val data: String = mbcli match {
      case _: MaBoSSQuClient => jobName + "\n" + DataStreamer.buildStreamData(clientData, hints)
      case _ => DataStreamer.buildStreamData(clientData, hints)
    }
    mbcli.send(data) match {
      case Some(s) => Some(new Result(simulation, hints.verbose, hints.hexfloat, s))
      case None => None
    }
  }
}

/** Results of MaBoSS server, <i>necessary for handling output of MaBoSS server.</i>
  *
  * @param simulation configuration and network
  * @param verbose    flag for parser of data
  * @param hexfloat   flag for writing data to file
  * @param outputData raw data from MaBoSS server
  */
class Result(val simulation: CfgMbss, verbose: Boolean, hexfloat: Boolean, outputData: String) extends ResultProcessing {

  /** Generates String or hexString from Double, according to hexfloat
    *
    * @param double input double
    * @return
    */
  def doubleToMbssString(double: Double): String = { // not yet used for writing files
    if (hexfloat) java.lang.Double.toHexString(double) else double.toString
  }

  var parsedResultData: ResultData =  DataStreamer.parseStreamData(outputData, verbose)
  val linesWithTime: List[String] = parsedResultData.prob_traj.split("\n").toList.tail
  lazy val probDistTrajectory: List[(Double,Map[Set[String], Double])] = linesWithTime.map(line => ResultMethods.lineToTimeProb(line))
  val sizes: List[Double] = List.fill(linesWithTime.length)(1.0)

  /** updates last probability distribution for UPMaBoSS
    *
    * @param divNode   division node
    * @param deathNode death node
    * @return (new_statistical_distribution,normalization_factor)
    */
  def updateLastLine(divNode: String, deathNode: String, verbose: Boolean = false): (Map[Set[String], Double], Double) = {
    ResultMethods.updateLine(parsedResultData.prob_traj.split("\n").toList.last, divNode, deathNode, verbose)
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
    * @param cfgMbss         input of simulation
    * @param hints           hint for server
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
trait ParReducibleProbDist extends ParReducibleRun[Map[Set[String], Double]] {
  protected def linCombine(fpMap1: Map[Set[String], Double], fpMap2: Map[Set[String], Double]): Map[Set[String], Double] = {
    (fpMap1.toList ::: fpMap2.toList).groupBy(_._1.toSet).map(x => (x._2.head._1 -> x._2.map(_._2).sum))
  }

  protected def multiply(fpMap: Map[Set[String], Double], d: Double): Map[Set[String], Double] = {
    fpMap.map(x => (x._1 -> x._2 * d))
  }
}

/** Concrete application of ParReducible run for fixed point distribution
  *
  */
object ParReducibleFP extends ParReducibleProbDist {
  protected def generate(r: Result): Map[Set[String], Double] = r.parsedResultData.FP.split("\n").tail.tail.
    map(line => {
      val lSplit = line.split("\t");
      ((if (lSplit(1) == "<nil>"){Set[String]()}else{lSplit(1).split(" -- ").toSet}) -> lSplit(0).toDouble)
    }).toMap
}

/** Concrete application of ParReducible run for last probability distribution
  *
  */
object ParReducibleLastLine extends ParReducibleProbDist {
  /** Generate probability distribution from last line of probtraj
    *
    * @param r Result
    * @return
    */
  protected def generate(r: Result): Map[Set[String], Double] = {
    ResultMethods.lineToTimeProb(r.parsedResultData.prob_traj.split("\n").last)._2
  }
}

/** Methods for handling results, mainly used by UPMaBoSS
  *
  */
object ResultMethods {
  /** initial condition and normalization factor from a line of probtraj, useful for UPMaBoSS
    *
    * @param line      line of probtraj
    * @param divNode   division node
    * @param deathNode death node
    * @param verbose   true for printing updating process
    * @return
    */
  def updateLine(line: String, divNode: String, deathNode: String, verbose: Boolean = false):
  (Map[Set[String], Double], Double) =
    updateProb(lineToTimeProb(line)._2, divNode, deathNode, verbose)

  /** initial condition and normalization factor from a probability distribution
    *
    * @param probDist  probability distribution over network states
    * @param divNode   division node
    * @param deathNode death node
    * @param verbose   true for printing updating process
    * @return
    */
  def updateProb(probDist: Map[Set[String], Double], divNode: String, deathNode: String,
                 verbose: Boolean = false): (Map[Set[String], Double], Double) = {
    val nonNormDist: Map[Set[String], Double] = probDist.
      filter(x => !x._1.contains(deathNode)).
      map(x => (if (x._1.contains(divNode)) {
        x._1 -> x._2 * 2
      } else {
        x._1 -> x._2
      })).groupBy(x => (x._1 - divNode)).map(x => (x._1 -> (x._2.values.sum)))
    val normFactor = nonNormDist.values.sum
    if (verbose) println("Norm. factor: " + normFactor)
    (nonNormDist.map(x => (x._1 -> (x._2 / normFactor))), normFactor)
  }

  /** transform line of probtraj in time and probablity distribution
    *
    * @param line probtraj line
    * @return
    */
  def lineToTimeProb(line: String): (Double, Map[Set[String], Double]) = {
    if (line == "") {
      (0,Map())
    } else {
      val splitLine = line.split("\t")
      (splitLine.head.toDouble,
        splitLine.dropWhile("^[0-9].*".r.findFirstIn(_).isDefined).sliding(3, 3).
          map(x => (
            (if (x(0) == "<nil>") {
              Set[String]()
            } else (x(0).split(" -- ").toSet)) -> x(1).toDouble)).toMap)
    }
  }

}

/** trait for handling results
  *
  */
trait ResultProcessing {

  /** list of probability distribution with time
    *
    * @return
    */
  def probDistTrajectory: List[(Double, Map[Set[String], Double])]


  /** write probability trajectory to file, <i>necessary for downloading results with class ResultFromFile</i>.
    * <br>Probability variance is not written
    *
    * @param filename name of file
    */
  def writeLinesWithTime(filename: String, hexString: Boolean = false): Unit = { //to be tested
    val pw = new PrintWriter(new File(filename))
    pw.write(
      probDistTrajectory.map(elm => elm._1 + "\t" +
        elm._2.map(prob =>
          (if (prob._1.isEmpty) {"<nil>"} else {prob._1.mkString(" -- ")}) + "\t" +
          (if (hexString) {java.lang.Double.toHexString(prob._2)} else {prob._2.toString})
        ).mkString("\t")).mkString("\n"))
    pw.close()
  }


  /** list of size, useful for UPMaBoSS
    *
    * @return
    */
  def sizes: List[Double] // careful: concrete class need to have same length with linesWithTime

  /** write size to file,
    *<i>necessary for downloading results with class ResultFromFile</i>
    * @param filename name of file
    */
  def writeSizes(filename: String, hexString: Boolean = false): Unit = { //to be tested
    val pw = new PrintWriter(new File(filename))
    pw.write(sizes.map(s =>
      (if (hexString) {java.lang.Double.toHexString(s)} else {s.toString})).mkString("\n"))
    pw.close()
  }

  /** Boolean state probability trajectory, given a network state,
    *<i>useful data processing method</i>
    * @param netState     network state, can be defined on a node subset
    * @param normWithSize if true probabilities are multiplied by sizes
    * @return probability over time
    */
  def stateTrajectory(netState: NetState, normWithSize: Boolean = false): List[(Double, Double)] = {
    val res : List[(Double,Double)]= probDistTrajectory.map(timeProbDist =>
      (timeProbDist._1, timeProbDist._2.filter(prob =>
            netState.activeNodes.diff(prob._1).isEmpty & netState.inactiveNodes.intersect(prob._1).isEmpty).values.sum))
    if (normWithSize) res.zip(sizes).map(x=>(x._1._1,x._1._2*x._2))
    else res
  }

  /** Node state probability trajectory, given a node,
    *<i>useful data processing method</i>
    * @param node         network node
    * @param normWithSize if true probabilities are multiplied by sizes
    * @return probability over time
    */
  def nodeTrajectory(node: String, normWithSize: Boolean = false): List[(Double, Double)] = { // to be tested, the .isDefined
    val res: List[(Double, Double)] = probDistTrajectory.map(timeProbDist =>
      (timeProbDist._1, timeProbDist._2.filter(prob => prob._1.contains(node)).values.sum))
    if (normWithSize) res.zip(sizes).map(x => (x._1._1, x._1._2 * x._2))
    else res
  }

  /** Plot Boolean state probability trajectories, given a list of probtraj and a list of network states,
    *<i>useful visualization method</i>
    * @param netStates    network state, can be defined on a node subset
    * @param firstLast    first (start at 1) and last elements to take in the trajectory
    * @param normWithSize if true probabilities are multiplied by sizes
    * @param filename     file name
    * @return
    */
  def plotStateTraj(netStates: List[NetState],
                    firstLast: (Int, Int) = (1, probDistTrajectory.length),
                    normWithSize: Boolean = false, filename: String): File = {
    val yLim = if (normWithSize) None else Some(0.0, 1.0)
    val yLab = if (normWithSize) "Rel. Size" else "Probability"
    val listTraj = netStates.map(x => stateTrajectory(x, normWithSize).slice(firstLast._1 - 1, firstLast._2))
    val Mat4Plot: Mat[Double] = Mat((Vec(listTraj.head.map(_._1).toArray) :: //matrix with x coordinates
      listTraj.map(x => Vec(x.map(y => y._2).toArray)) ::: // y coordinates
      (1 to netStates.length).map(x => Vec(List.fill(listTraj.head.length)(x.toDouble - 1).toArray)).toList).toArray) //and colors
    val builtElement =
      xyplot(Mat4Plot -> (
        (1 to netStates.length).map(x => line(xCol = 0, yCol = x, colorCol = netStates.length + x, // lines given the column indices of the matrix
          color = DiscreteColors(netStates.length - 1))).toList :::
          (1 to netStates.length).map(x => // points given the column indices of the matrix
            point(xCol = 0, yCol = x, colorCol = netStates.length + x, sizeCol = 3 + 2 * netStates.length,
              shapeCol = 3 + 2 * netStates.length, errorBottomCol = 1 + netStates.length, errorTopCol = 1 + netStates.length, size = 4d,
              color = DiscreteColors(netStates.length - 1))).toList) // careful, need to add zero to errorTop/Bottom
      )(xlab = "Time", ylab = yLab, extraLegend =
        netStates.zipWithIndex.map(x => x._1.toString -> PointLegend(shape = Shape.rectangle(0, 0, 1, 1),
          color = DiscreteColors(netStates.length - 1)(x._2.toDouble))), ylim = yLim, xWidth = RelFontSize(40d))
    val pdfFile = new File(filename)
    pdfToFile(pdfFile, sequence(builtElement :: Nil, FreeLayout).build)
  }

  /** Write tab-separated file of state probability trajectory,
    *<i>useful data exporting method</i>
    * @param netStates network state, can be defined on a node subset
    * @param filename  file name
    */
  def writeStateTraj(netStates: List[NetState],
                     firstLast: (Int, Int) = (1, probDistTrajectory.length),
                     normWithSize: Boolean = false, filename: String): Unit = {
    val pw = new PrintWriter(new File(filename))
    val header = "Time\t" + netStates.map(x => x.toString).mkString("\t") + "\n"
    pw.write(header)
    val listTraj = netStates.map(x => stateTrajectory(x, normWithSize).slice(firstLast._1 - 1, firstLast._2))
    val timeList: Vector[Double] = listTraj.head.map(_._1).toVector
    val flatVectorProb: Vector[Double] = listTraj.flatten.map(_._2).toVector
    probDistTrajectory.indices.foreach(lineIndex => {
      pw.write(timeList(lineIndex).toString)
      netStates.indices.foreach(stateIndex =>
        pw.write("\t" + flatVectorProb(stateIndex * probDistTrajectory.length + lineIndex)).toString)
      pw.write("\n")
    })
    pw.close()
  }

}

/** Concrete class of result from files (probDistTrajectory and sizes), produced by the methods
  *  writeLinesWithTime and writeSizes of the trait ResultProcessing.
  *
  *
  * @param filenameLinesWithTime not in MaBoSS probtraj format, in format of writeLinesWithTime
  * @param filenameSize          in format writeSizes
  * @param listNodes             list of nodes for constructing NetState
  */
class ResultFromFile(val filenameLinesWithTime: String, val filenameSize: String, val listNodes: List[String]) extends ResultProcessing {
  val probDistTrajectory: List[(Double, Map[Set[String], Double])] = ManageInputFile.file_get_content(filenameLinesWithTime).
    split("\n").map(line => {
    val lineSplit = line.split("\t")
    (lineSplit.head.toDouble, lineSplit.tail.sliding(2, 2).
      map(stateProb => ((if (stateProb(0) == "<nil>"){Set[String]()}else{stateProb(0).split(" -- ").toSet}) -> stateProb(1).toDouble)).toMap)
  }).toList
  val sizes: List[Double] = ManageInputFile.file_get_content(filenameSize).split("\n").toList.map(_.toDouble)
}

/** Result from MaBoSS output probtraj file.
  *
  * @param probtrajFileName
  * @param listNodes
  */
class ResultFromMSSOutFile(val probtrajFileName: String,val listNodes: List[String]) extends ResultProcessing {
  val probDistTrajectory : List[(Double, Map[Set[String], Double])] = ManageInputFile.file_get_content(probtrajFileName).
    split("\n").toList.tail.map(l => ResultMethods.lineToTimeProb(l))
  val sizes :List[Double] = List.fill(probDistTrajectory.length)(1)
}