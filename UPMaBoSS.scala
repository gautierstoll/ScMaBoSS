package ScMaBoSS

import org.apache.commons.lang.ObjectUtils.Null
import scalatags.Text.short.*
import java.io._

import scala.util.Random
import scala.util.matching.Regex

/** Methods for UPMaBoSS: instancing UPMaBoSS from file, update external variables
  *
  */
object UPMaBoSS {

  private def fromFiles(upFile: String, cfg: CfgMbss,port : Int, hexUP:Boolean,verbose : Boolean):
    (String,String,List[String],Int, Int,CfgMbss,Int ,Boolean,Boolean) = {
    val upLines : List[String] = ManageInputFile.file_get_content(upFile).split("\n").toList
    val deathNode : String = upLines.filter(x => "death\\s*=".r.findFirstIn(x).isDefined) match {
      case Nil => ""
      case deathList: List[String] => "[\\s;]*".r.replaceAllIn("\\s*death\\s*=\\s*".r.replaceAllIn(deathList.head,""),"")
    }
    val divisionNode : String = upLines.filter(x => "division\\s*=".r.findFirstIn(x).isDefined) match {
      case List() => ""
      case divisionList: List[String] => "[\\s;]*".r.replaceAllIn("\\s*division\\s*=\\s*".r.replaceAllIn(divisionList.head,""),"")
    }
    val steps : Int = upLines.filter(x => "steps\\s*=".r.findFirstIn(x).isDefined) match {
      case List() => 1
      case stepList: List[String] => "[\\s;]*".r.replaceAllIn("\\s*steps\\s*=\\s*".r.replaceAllIn(stepList.head,""),"").toInt
    }
    val seed : Int = upLines.filter(x => "seed\\s*=".r.findFirstIn(x).isDefined) match {
      case List() => 0
      case seedList: List[String] => "[\\s;]*".r.replaceAllIn("\\s*seed\\s*=\\s*".r.replaceAllIn(seedList.head,""),"").toInt
    }
    val updateVar : List[String]= upLines.filter(x => "u=".r.findFirstIn(x).isDefined)
    (divisionNode,deathNode,updateVar,steps,seed,cfg,port,hexUP,verbose)
  }



  /** Applies init condition for updating external variables
    *
    * @param initCondProb
    * @param upProb
    * @return
    */
  private def upProbFromInitCond(initCondProb : List[(String,Double)] , upProb : String,hex :Boolean = false) : String = {
    //println("upProb: "+upProb)
    val nodes = try upProb.split("=").head catch
      {case _:Throwable => throw new IllegalArgumentException("cannot parse "+upProb)}
    val boolState = try upProb.split("=").tail.head catch
      {case _:Throwable => throw new IllegalArgumentException("cannot parse "+upProb)}
    val nodeList = "\\s*\\)\\s*".r.replaceAllIn("p\\[\\s*\\(\\s*".r.replaceAllIn(nodes,""),"").split(",").
      map(x=> "\\s*".r.replaceAllIn(x,"")).toList
    //println("nodeList: "+nodeList)
    val boolStateList : List[Boolean] = "\\s*\\)\\s*\\]".r.replaceAllIn("\\s*\\(\\s*".r.replaceAllIn(boolState,""),"").split(",").
      map(x=> if("\\s*".r.replaceAllIn(x,"") == "1") true else false).toList
    //println("boolStateList: "+boolStateList)
    val activeNodes = nodeList.zip(boolStateList).filter(_._2).map(_._1).toSet
    val inactiveNodes = nodeList.zip(boolStateList).filter(!_._2).map(_._1).toSet
    val probOut = initCondProb.filter(prob => {
      val activeProbNodes = prob._1.split(" -- ").toSet
      activeNodes.subsetOf(activeProbNodes) & activeProbNodes.intersect(inactiveNodes).isEmpty
    }).map(_._2).sum
    if (hex) java.lang.Double.toHexString(probOut) else probOut.toString
  }
}

/**UPMaBoSS in scala, using MaBoSS server
  *
  * @param divNode
  * @param deathNode
  * @param updateVar
  * @param steps
  * @param seed necessary because '#rand' can be in updateVar
  * @param cfgMbss
  * @param portMbss port of MaBoSS server
  * @param hexUP using hexString in cfg?
  * @param verbose show normalization factor and update variables when running?
  */
class UPMaBoSS(val divNode : String, val deathNode : String, val updateVar : List[String], val steps : Int,
               val seed:Int, val cfgMbss : CfgMbss, portMbss : Int , hexUP : Boolean = false,verbose:Boolean = false) {
  private def this(t : (String,String,List[String],Int, Int,CfgMbss,Int ,Boolean,Boolean)) =
    this(t._1,t._2,t._3,t._4,t._5,t._6,t._7,t._8,t._9)

  /** Constructor from files
    *
    * @param upFile
    * @param cfg
    * @param port
    * @param hexUP
    * @param verbose
    * @return
    */
  def this(upFile: String, cfg: CfgMbss,port : Int, hexUP:Boolean,verbose : Boolean) =
    this(UPMaBoSS.fromFiles(upFile, cfg,port, hexUP,verbose))

  /** Constructor from files, verbose is false
    *
    * @param upFile
    * @param cfg
    * @param port
    * @param hexUP
    * @return
    */
  def this(upFile: String, cfg: CfgMbss,port : Int, hexUP:Boolean) =
    this(upFile, cfg,port, hexUP,false)

  /** Constructor from files, hexUP and verbose are false
    *
    * @param upFile
    * @param cfg
    * @param port
    * @return
    */
  def this(upFile: String, cfg: CfgMbss,port : Int) =
    this(upFile, cfg,port,false)

  def writeToFile(filename : String) : Unit = {
    val pw = new PrintWriter(new File(filename))
    pw.write("death = "+deathNode+"\n")
    pw.write("division = "+divNode+"\n")
    pw.write("death = "+deathNode+"\n")
    pw.write(updateVar.mkString("\n")+"\n")
    pw.write("steps = "+steps.toString+"\n")
    pw.write("seed = "+seed.toString+"\n")
    pw.close()
  }
  val updateVarNames : List[String] = updateVar.map(x => "\\s*".r.replaceAllIn("u=.*".r.replaceAllIn(x,""),""))
  val upRandom: Random = new Random(seed) // for UPMaBoSS producing full data
  val upRandom4Light: Random = new Random(seed) // for UPMaBoSS producing light data
  val hints : Hints = Hints(hexfloat = hexUP)

  private def setUpdateVar(probDistRelSize : (List[(String,Double)],Double),upRnd : Random,verbose:Boolean = verbose) : String = {
    def recReplaceRand(s:String) : String = {
      "#rand".r.findFirstIn(s) match {
        case None => s
        case Some(c) =>
          recReplaceRand("#rand".r.replaceFirstIn(s,
            if (hexUP) java.lang.Double.toHexString(upRnd.nextDouble()) else upRnd.nextDouble().toString))
      }
    }
    val updateVarProb = updateVar.map(line => {
      val listReplaceProb : List[String] =
        "p\\[[^\\]]+\\]".r.findAllIn(line).map(x=>UPMaBoSS.upProbFromInitCond(probDistRelSize._1,x,hexUP)).toList
      def recReplace(regex: Regex,s:String,lReplace : List[String]):String = {
        lReplace match {
          case Nil => s
          case l => recReplace(regex, regex.replaceFirstIn(s, l.head), l.tail)
        }
      }
        recReplace("p\\[[^\\]]+\\]".r,line,listReplaceProb)
      }).map(x=>"u=".r.replaceAllIn(x,"=")).mkString("\n")
    val updatePopRatio = "#pop_ratio".r.replaceAllIn(updateVarProb,if (hexUP) java.lang.Double.toHexString(probDistRelSize._2) else probDistRelSize._2.toString)
    if (verbose) {
      val replaceString = recReplaceRand(updatePopRatio)
      println("Update variables: "+replaceString)
      replaceString
    } else recReplaceRand(updatePopRatio)
  }

  /** Steps of UPMaBoSS
    *
    * @param cfgMbss
    * @param result
    * @param relSize
    */
  case class UpStep(cfgMbss: CfgMbss, result: Result = null, relSize : Double = 1d) {}

  private def upDate(upStep : UpStep) : UpStep = {
    if (upStep.relSize == 0) UpStep(null,null,0) else {
      val mcli = new MaBoSSClient("localhost",portMbss)
      val newResult = mcli.run(upStep.cfgMbss, hints)
      val newInitCond = newResult.updateLastLine(divNode, deathNode,verbose)
      val newRelSize = upStep.relSize * newInitCond._2
      println("New relative size: "+newRelSize)
      val newInitCondCfg = upStep.cfgMbss.setInitCond(newInitCond._1.map(x => (new NetState(x._1, cfgMbss), x._2)), hex = hexUP)
      println("New initial condition")
      val newCfgString = updateVarNames match {
        case Nil => newInitCondCfg.cfg + "\n" +
          setUpdateVar(newInitCond,upRandom)
        case l => newInitCondCfg.cfg.split("\n").
          filter(x => !updateVarNames.map(name => ("\\"+name+"\\s*=").r.findFirstIn(x).isDefined).reduce(_ | _)).
          mkString("\n") + "\n" + setUpdateVar(newInitCond,upRandom)
      }
      println("External variable updated")
      val newCfg = new CfgMbss(newInitCondCfg.bndMbss, newCfgString)
      UpStep(newCfg, newResult, newRelSize)
    }
  }

  /** Stream made of UpStep
    *
    */
  val strRun : Stream[UpStep] = UpStep(cfgMbss) #:: strRun.map(res => upDate(res))


  /**Run UpMaBoSS, with full list of configurations. Useful for model debugging. Because it uses the stream strRun,
    * it can be relaunched with a larger number of steps.
    *
    * @param nbSteps
    * @return relative sizes and configuration for each step
    */
  def run(nbSteps : Int = steps) : UPMbssOut = {
    val listRun = strRun.zipWithIndex.map(x => {println("Step: "+(x._2+1));x._1}).take(nbSteps).toList
    UPMbssOut(listRun.map(_.relSize),listRun.map(_.cfgMbss))
  }

  /** Light version of UpStep
    *
    * @param lastLineProbTraj
    * @param relSize
    */
  case class UpStepLight(lastLineProbTraj : Option[String] = None, relSize : Double = 1d) {}

  private def upDateLight(upStep : UpStepLight) : UpStepLight = {
    if (upStep.relSize == 0) UpStepLight(None,0) else {
      val newInitCond = upStep.lastLineProbTraj match {
        case None => None
        case Some(line) => Some(Result.updateLine(line, divNode, deathNode,verbose))
      }
      val newRelSize = newInitCond match {
        case None => upStep.relSize
        case Some((dist, ratio)) => upStep.relSize * ratio
      }
      val newCfg = newInitCond match {
        case None => cfgMbss
        case Some((dist, ratio)) => {
          val newInitCondCfg = cfgMbss.setInitCond(dist.map(x => (new NetState(x._1, cfgMbss), x._2)), hex = hexUP)
          val newCfgString = updateVarNames match {
            case Nil => newInitCondCfg.cfg + "\n" +
              setUpdateVar((dist, ratio),upRandom4Light)
            case l => newInitCondCfg.cfg.split("\n").
              filter(x => !updateVarNames.map(name => name.r.findFirstIn(x).isDefined).reduce(_ | _)).
              mkString("\n") + "\n" + setUpdateVar((dist, ratio),upRandom4Light)
          }
          new CfgMbss(newInitCondCfg.bndMbss, newCfgString)
        }
      }
      val mcli = new MaBoSSClient("localhost",portMbss)
      val result = mcli.run(newCfg, hints)
      UpStepLight(Some(result.parsedResultData.prob_traj.split("\n").toList.last), newRelSize)
    }
  }

  /** Stream made of UpStepLight
    *
    */
  val strRunLight : Stream[UpStepLight] = UpStepLight() #:: strRunLight.map(res => upDateLight(res))//careful, simulation data start at index 1
  /** Run UpMaBoSS, with minimal output. Because it uses the stream strRunLight, it can be relaunched with a larger number of
    * steps.
    *
    * @param nbSteps
    * @return minimal outputs of UPMaBoSS
    */
  def runLight(nbSteps : Int = steps) : UPMbssOutLight = {
    val listRun = strRunLight.zipWithIndex.map(x => {println("Step: "+x._2);x._1}).take(nbSteps+1).toList.tail
    UPMbssOutLight(listRun.map(_.relSize),listRun.map(x => x.lastLineProbTraj match {case None => "";case Some(s) => s}),cfgMbss)
  }
}

/** Output of UPMaBoSS, including population size and CfgMbss for each time steps
  *
  * @param sizes
  * @param configurations
  */
case class UPMbssOut(sizes : List[Double], configurations : List[CfgMbss]) {}

/** Minimal output of UPMaBoSS, including population size and protraj last line of each MaBoSS run
  *
  * @param sizes
  * @param lastLines
  */
case class UPMbssOutLight(sizes : List[Double], lastLines : List[String],cfgMbss : CfgMbss ) extends ResultProcessing {
  val stepTime : Double = "=(.*);".r.findAllIn(cfgMbss.noCommentCfg.split("\n").filter("max_time".r.findFirstMatchIn(_).isDefined).head).
    matchData.map(_.group(1).toDouble).next()
  /** Last probtraj line with UPMaBoSS time
    *
    */
  val linesWithTime : List[String] = lastLines.zipWithIndex.
    map(lineIndex => {"^[\t]*".r.replaceAllIn(lineIndex._1,(lineIndex._2*stepTime).toString+"\t")})

}