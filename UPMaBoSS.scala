package ScMaBoSS

import org.apache.commons.lang.ObjectUtils.Null
import scalatags.Text.short.*
import java.io._

import scala.collection.immutable.Map
import scala.util.Random
import scala.util.matching.Regex

/** Methods for UPMaBoSS: instancing UPMaBoSS from file, update external variables
  *
  */
object UPMaBoSS {
  /**
    *
    * @param upFile  upp file
    * @param cfg     cfg file
    * @param host    ip of MaBoSS server
    * @param port    port of MaBoSS server
    * @param hexUP   true for using hexString
    * @param verbose true for priting updating process
    * @return
    */
  private def fromFiles(upFile: String, cfg: CfgMbss, host: String, port: Int, hexUP: Boolean, verbose: Boolean):
  (String, String, List[String], Int, Int, CfgMbss, String, Int, Boolean, Boolean) = {
    val upLines: List[String] = ManageInputFile.file_get_content(upFile).split("\n").toList
    val deathNode: String = upLines.filter(x => "death\\s*=".r.findFirstIn(x).isDefined) match {
      case Nil => ""
      case deathList: List[String] => "[\\s;]*".r.replaceAllIn("\\s*death\\s*=\\s*".r.replaceAllIn(deathList.head, ""), "")
    }
    val divisionNode: String = upLines.filter(x => "division\\s*=".r.findFirstIn(x).isDefined) match {
      case List() => ""
      case divisionList: List[String] => "[\\s;]*".r.replaceAllIn("\\s*division\\s*=\\s*".r.replaceAllIn(divisionList.head, ""), "")
    }
    val steps: Int = upLines.filter(x => "steps\\s*=".r.findFirstIn(x).isDefined) match {
      case List() => 1
      case stepList: List[String] => "[\\s;]*".r.replaceAllIn("\\s*steps\\s*=\\s*".r.replaceAllIn(stepList.head, ""), "").toInt
    }
    val seed: Int = upLines.filter(x => "seed\\s*=".r.findFirstIn(x).isDefined) match {
      case List() => 0
      case seedList: List[String] => "[\\s;]*".r.replaceAllIn("\\s*seed\\s*=\\s*".r.replaceAllIn(seedList.head, ""), "").toInt
    }
    val updateVar: List[String] = upLines.filter(x => "u=".r.findFirstIn(x).isDefined)
    (divisionNode, deathNode, updateVar, steps, seed, cfg, host, port, hexUP, verbose)
  }

  /** Applies init condition for updating external variables
    *
    * @param initCondProb Probability distribution
    * @param upProb       a written probability, eg p[(a,b) = (0,1)]
    * @param hex          true if HexString
    * @return probability value written in String or in HexString
    */
  private def upProbFromInitCond(initCondProb: Map[Set[String], Double], upProb: String, hex: Boolean = false): String = {
    val nodes = try upProb.split("=").head catch {
      case _: Throwable => throw new IllegalArgumentException("cannot parse " + upProb)
    }
    val boolState = try upProb.split("=").tail.head catch {
      case _: Throwable => throw new IllegalArgumentException("cannot parse " + upProb)
    }
    val nodeList = "\\s*\\)\\s*".r.replaceAllIn("p\\[\\s*\\(\\s*".r.replaceAllIn(nodes, ""), "").split(",").
      map(x => "\\s*".r.replaceAllIn(x, "")).toList
    val boolStateList: List[Boolean] = "\\s*\\)\\s*\\]".r.replaceAllIn("\\s*\\(\\s*".r.replaceAllIn(boolState, ""), "").split(",").
      map(x => if ("\\s*".r.replaceAllIn(x, "") == "1") true else false).toList
    val activeNodes = nodeList.zip(boolStateList).filter(_._2).map(_._1).toSet
    val inactiveNodes = nodeList.zip(boolStateList).filter(!_._2).map(_._1).toSet
    val probOut: Double = initCondProb.filter(x => (activeNodes.diff(x._1).isEmpty & x._1.intersect(inactiveNodes).isEmpty)).values.sum
    if (hex) java.lang.Double.toHexString(probOut) else probOut.toString
  }
}

/** UPMaBoSS in scala, using MaBoSS server
  *
  * @param divNode   division node
  * @param deathNode deathe node
  * @param updateVar list of update var lines
  * @param steps     number of steps
  * @param seed      necessary because '#rand' can be in updateVar
  * @param cfgMbss   cfg
  * @param portMbss  port of MaBoSS server
  * @param hexUP     using hexString in cfg?
  * @param verbose   show normalization factor and update variables when running?
  */
class UPMaBoSS(val divNode: String, val deathNode: String, val updateVar: List[String], val steps: Int,
               val seed: Int, val cfgMbss: CfgMbss, val hostMbss: String, val portMbss: Int,
               val hexUP: Boolean,val verbose: Boolean) {

  private def this(t: (String, String, List[String], Int, Int, CfgMbss, String, Int, Boolean, Boolean)) =
    this(t._1, t._2, t._3, t._4, t._5, t._6: CfgMbss, t._7, t._8, t._9, t._10)

  /** Constructor from files
    *
    * @param upFile  upp file
    * @param cfg     cfg
    * @param port    port of MaBoSS server
    * @param hexUP   true for using HexString
    * @param verbose true for showing normalization factor and update variables when running
    * @return
    */
  def this(upFile: String, cfg: CfgMbss, host: String = "localhost", port: Int, hexUP: Boolean = false , verbose: Boolean = false ) =
    this(UPMaBoSS.fromFiles(upFile, cfg, host, port, hexUP, verbose))

  /** write upp file
    *
    * @param filename upp file name
    */
  def writeToFile(filename: String): Unit = {
    val pw = new PrintWriter(new File(filename))
    pw.write("death = " + deathNode + "\n")
    pw.write("division = " + divNode + "\n")
    pw.write("death = " + deathNode + "\n")
    pw.write(updateVar.mkString("\n") + "\n")
    pw.write("steps = " + steps.toString + "\n")
    pw.write("seed = " + seed.toString + "\n")
    pw.close()
  }

  val updateVarNames: List[String] = updateVar.map(x => "\\s*".r.replaceAllIn("u=.*".r.replaceAllIn(x, ""), "")) //same order than updateVar
  val upRandom: Random = new Random(seed) // for UPMaBoSS producing full data
  val upRandom4Light: Random = new Random(seed) // for UPMaBoSS producing light data
  val hints: Hints = Hints(hexfloat = hexUP)
  var timeMaBoSS : Long = 0
  var timeMaBoSSServer : Long = 0
  /** return a list of string containing the updated variables with their values, in same order than updateVar, to be put in cfg
    *
    * @param probDistRelSize probability distribution and relative size
    * @return
    */
  private def setUpdateVar(probDistRelSize: (Map[Set[String], Double], Double)): List[String] = { // same order than updateVar and updateVarNames
    updateVar.map(line => {
      val listReplaceProb: List[String] =
        "p\\[[^\\]]+\\]".r.findAllIn(line).map(x => UPMaBoSS.upProbFromInitCond(probDistRelSize._1, x, hexUP)).toList

      def recReplace(regex: Regex, s: String, lReplace: List[String]): String = {
        lReplace match {
          case Nil => s
          case l => recReplace(regex, regex.replaceFirstIn(s, l.head), l.tail)
        }
      }

      recReplace("p\\[[^\\]]+\\]".r, line, listReplaceProb)
    }).map(line => "u=".r.replaceAllIn(line, "=")).
      map(line => "#pop_ratio".r.replaceAllIn(line, if (hexUP) java.lang.Double.toHexString(probDistRelSize._2) else probDistRelSize._2.toString))
  }

  /** recursive replacement of '#rand" word by random value
    *
    * @param s     string
    * @param upRnd random number generator
    * @return
    */
  private def recReplaceRand(s: String, upRnd: Random): String = {
    "#rand".r.findFirstIn(s) match {
      case None => s
      case Some(c) =>
        recReplaceRand("#rand".r.replaceFirstIn(s,
          if (hexUP) java.lang.Double.toHexString(upRnd.nextDouble()) else upRnd.nextDouble().toString), upRnd)
    }
  }

  /** update cfg according to updatedVar lines and random number generator for '#rand' keyword
    *
    * @param cfg        cfg
    * @param updatedVar lines of updated variable with their updated values
    * @param upRnd      random number generator
    * @return
    */
  private def updateCfg(cfg: String, updatedVar: List[String], upRnd: Random): String = {
    val nameUpdateVar = updateVarNames.zip(updatedVar) // updated var is in same order that updataVarName
    val newCfg = cfg.split("\n").map(line => {
      val varName = "\\s*".r.replaceAllIn("=.*".r.replaceAllIn(line, ""), "")
      val updateVarCatch = nameUpdateVar.filter(n => (n._1 == varName))
      if (updateVarCatch.isEmpty) line else {
        updateVarCatch.head._2
      }
    }
    ).mkString("\n")
    recReplaceRand(newCfg, upRnd)
  }

  /** Steps of UPMaBoSS, for constructing the stream strRun
    *
    * @param cfgMbss cfg
    * @param result  result
    * @param relSize relative size
    */
  case class UpStep(cfgMbss: CfgMbss, result: Result = null, relSize: Double = 1d) {}

  private def upDate(upStep: UpStep): UpStep = {
    if (upStep.relSize == 0) UpStep(null : CfgMbss, null, 0) else {
      MaBoSSClient(hostMbss, portMbss) match {
        case Some(mcli) => {
          mcli.run(upStep.cfgMbss, hints) match {
            case Some(newResult : Result) => {
              val newInitCond = newResult.updateLastLine(divNode, deathNode, verbose)
              val newRelSize = upStep.relSize * newInitCond._2
              println("New relative size: " + newRelSize)
              val newInitCondCfg = upStep.cfgMbss.
                setInitCond(newInitCond._1, hex = hexUP)
              println("New initial condition")
              val newCfgString = updateCfg(newInitCondCfg.cfg, setUpdateVar(newInitCond._1, newRelSize), upRandom)
              println("External variable updated")
              if (verbose) newCfgString.split("\n").
                filter(line => updateVarNames.contains("\\s*".r.replaceAllIn("=.*".r.replaceAllIn(line, ""), ""))).
                foreach(line => println(line))
              val newCfg = new CfgMbss(newInitCondCfg.bndMbss, newCfgString)
              UpStep(newCfg, newResult: Result, newRelSize)
            }
            case None => UpStep(null : CfgMbss, null, 1d)
          }
        }
        case None => UpStep(null: CfgMbss, null, 1d)
      }
    }
  }

  /** Stream made of UpStep
    *
    */
  val strRun: Stream[UpStep] = UpStep(cfgMbss) #:: strRun.map(res => upDate(res))


  /** Run UpMaBoSS, with full list of configurations. Useful for model debugging. Because it uses the stream strRun,
    * it can be relaunched with a larger number of steps.
    *
    * @param nbSteps number of steps
    * @return relative sizes and configuration for each step
    */
  def run(nbSteps: Int = steps): UPMbssOut = {
    val listRun = strRun.zipWithIndex.map(x => {
      println("Step: " + (x._2 + 1)); x._1
    }).take(nbSteps).toList
    UPMbssOut(listRun.map(_.relSize), listRun.map(_.cfgMbss))
  }

  /** Light version of UpStep
    *
    * @param lastLineProbTraj last line with
    * @param relSize          relative size
    */
  case class UpStepLight(lastLineProbTraj: Option[String] = None, relSize: Double = 1d) {}

  private def upDateLight(upStep: UpStepLight): UpStepLight = {
    if (upStep.relSize == 0) UpStepLight(None, 0) else {
      val newInitCond = upStep.lastLineProbTraj match {
        case None => None
        case Some(line) => Some(ResultMethods.updateLine(line, divNode, deathNode, verbose))
      }
      val newRelSize = newInitCond match {
        case None => upStep.relSize
        case Some((dist, ratio)) => upStep.relSize * ratio
      }
      val newCfg = newInitCond match {
        case None => cfgMbss
        case Some((dist, ratio)) => {
          val newInitCondCfg = cfgMbss.setInitCond(dist, hex = hexUP)
          val newCfgString = this.updateCfg(newInitCondCfg.cfg, this.setUpdateVar((dist, newRelSize)), upRandom4Light)
          if (verbose) newCfgString.split("\n").
            filter(line => updateVarNames.contains("\\s*".r.replaceAllIn("=.*".r.replaceAllIn(line, ""), ""))).
            foreach(line => println(line))
          new CfgMbss(newInitCondCfg.bndMbss, newCfgString)
        }
      }
      MaBoSSClient(hostMbss, portMbss) match {
        case Some(mcli) => {
          val sysTime = System.currentTimeMillis()
          mcli.run(newCfg, hints) match {
            case Some(result) => {timeMaBoSS += (System.currentTimeMillis()-sysTime)
              timeMaBoSSServer += mcli.timeNext
              UpStepLight(Some(result.parsedResultData.prob_traj.split("\n").toList.last), newRelSize)}
            case None => UpStepLight(None, 1d)
          }
        }
        case None => UpStepLight(None, 1d)
      }
    }
  }

  /** Stream made of UpStepLight
    *
    */
  val strRunLight: Stream[UpStepLight] = UpStepLight() #:: strRunLight.map(res => upDateLight(res)) //careful, simulation data start at index 1
  /** Run UpMaBoSS, with minimal output. Because it uses the stream strRunLight, it can be relaunched with a larger number of
    * steps.
    *
    * @param nbSteps number of steps
    * @return minimal outputs of UPMaBoSS
    */
  def runLight(nbSteps: Int = steps): UPMbssOutLight = {
    val listRun = strRunLight.zipWithIndex.map(x => {
      println("Step: " + x._2); x._1
    }).take(nbSteps + 1).toList.tail
    UPMbssOutLight(listRun.map(_.relSize), listRun.map(x => x.lastLineProbTraj match {
      case None => "";
      case Some(s) => s
    }), cfgMbss)
  }
}

/** Output of UPMaBoSS, including population size and CfgMbss for each time steps, mainly used for full model debugging
  *
  * @param sizes          list of relative sizes
  * @param configurations list of cfg
  */
case class UPMbssOut(sizes: List[Double], configurations: List[CfgMbss]) {}

/** Minimal output of UPMaBoSS, including population size and protraj last line of each MaBoSS run, for processing results
  *
  * @param sizes     list of relaitve sizes
  * @param lastLines list of probtraj last lines
  */
case class UPMbssOutLight(sizes: List[Double], lastLines: List[String], cfgMbss: CfgMbss) extends ResultProcessing {
  val stepTime: Double = "=(.*);".r.findAllIn(cfgMbss.noCommentCfg.split("\n").filter("max_time".r.findFirstMatchIn(_).isDefined).head).
    matchData.map(_.group(1).toDouble).next()
  /** Last probtraj line with UPMaBoSS time
    *
    */
  val linesWithTime: List[String] = lastLines.zipWithIndex.
    map(lineIndex => {
      "^[\t]*".r.replaceAllIn(lineIndex._1, (lineIndex._2 * stepTime).toString + "\t")
    })

  val probDistTrajectory: List[(Double, Map[Set[String], Double])] = lastLines.zipWithIndex.map(lineIndex =>
    (lineIndex._2 * stepTime, ResultMethods.lineToTimeProb(lineIndex._1)._2))
}