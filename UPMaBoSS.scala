import org.apache.commons.lang.ObjectUtils.Null
import scalatags.Text.short.*

object UPMaBoSS {
  def fromFiles(upFile: String, cfg: CfgMbss): UPMaBoSS = {
    val upLines : List[String] = ManageInputFile.file_get_content(upFile).split("\n").toList
    val deathNode : String = upLines.filter(x => "\\s*death\\s*".r.findFirstIn(x).isDefined) match {
      case List() => ""
      case deathList: List[String] => "[\\s;]*".r.replaceAllIn("*\\s*death\\s*=\\s*".r.replaceAllIn(deathList.head,""),"")
    }
    val divisionNode : String = upLines.filter(x => "\\s*division\\s*".r.findFirstIn(x).isDefined) match {
      case List() => ""
      case divisionList: List[String] => "[\\s;]*".r.replaceAllIn("*\\s*division\\s*=\\s*".r.replaceAllIn(divisionList.head,""),"")
    }
    val steps : Int = upLines.filter(x => "\\s*step\\s*".r.findFirstIn(x).isDefined) match {
      case List() => 1
      case stepList: List[String] => "[\\s;]*".r.replaceAllIn("*\\s*division\\s*=\\s*".r.replaceAllIn(stepList.head,""),"").toInt
    }
    val seed : Int = upLines.filter(x => "\\s*seed\\s*".r.findFirstIn(x).isDefined) match {
      case List() => 0
      case seedList: List[String] => "[\\s;]*".r.replaceAllIn("*\\s*division\\s*=\\s*".r.replaceAllIn(seedList.head,""),"").toInt
    }
    val updateVar : List[(String,String)]= upLines.filter(x => "u=".r.findFirstIn(x).isDefined).
      map(x => x.split("\\s*u=\\s*").toList).
      map( x=> ("\\s*".r.replaceAllIn(x.head,""),x.tail.head))

    new UPMaBoSS(divisionNode,deathNode,updateVar,steps,seed,cfg)
  }
}
class UPMaBoSS(val divNode : String, val deathNode : String, val updateVar : List[(String,String)], steps : Int, val seed:Int, val cfgMbss : CfgMbss,hexUP : Boolean = false) {
  val hints : Hints = Hints(check = false,hexfloat = hexUP,augment = true,overRide = false,verbose = false)

  case class UpStep(cfgMbss: CfgMbss, result: Result = null, seed : Int = 0, relSize : Double = 1d) {}

  def upDate(upStep : UpStep) : UpStep = {
    val mcli = new MaBoSSClient(port = 4291)
    val newResult = mcli.run(upStep.cfgMbss, hints)
    mcli.close()
    val newInitCond = newResult.updateLastLine(divNode, deathNode)
    val newRelSize = upStep.relSize * newInitCond._2
    val newCfg = upStep.cfgMbss.setInitCond(newInitCond._1.map(x => (new NetState(x._1, cfgMbss), x._2)),hex = hexUP)

    // need to add update cfg according to newInitCond._2 and updateVar


    UpStep(newCfg, newResult, seed, newRelSize)
  }
  val strRun : Stream[UpStep] = UpStep(cfgMbss) #:: strRun.map(res => upDate(res))
  def run(nbSteps : Int) : UPMbssOut = {
    val listRun = strRun.zipWithIndex.map(x => {println("Step: "+(x._2+1));x._1}).take(nbSteps).toList
    UPMbssOut(listRun.map(_.relSize),listRun.map(_.cfgMbss))
  }

  case class UpStepLight(lastLineProbTraj : Option[String] = None, seed : Int = 0, relSize : Double = 1d) {}

  def upDateLight(upStep : UpStepLight) : UpStepLight = {
    val newInitCond = upStep.lastLineProbTraj match {
      case None => None
      case Some(line) => Some(Result.updateLine(line,divNode, deathNode))
    }
    val newRelSize = newInitCond  match {
          case None => upStep.relSize
          case Some((dist,ratio)) => upStep.relSize * ratio
        }
    val newCfg = newInitCond match {
      case None => cfgMbss
      case Some((dist,ratio)) => cfgMbss.setInitCond(dist.map(x => (new NetState(x._1, cfgMbss), x._2)),hex = hexUP ) // need to add update cfg according to newInitCond._2 and updateVar
    }
    val mcli = new MaBoSSClient(port = 4291)
    val result = mcli.run(newCfg, hints)
    mcli.close()
    UpStepLight(Some(result.parsedResultData.prob_traj.split("\n").toList.last),seed,newRelSize)
      }

  val strRunLight : Stream[UpStepLight] = UpStepLight() #:: strRunLight.map(res => upDateLight(res)) //careful, simulation data start at index 1
    def runLight(nbSteps : Int) : UPMbssOutLight = {
      val listRun = strRunLight.zipWithIndex.map(x => {println("Step: "+x._2);x._1}).take(nbSteps+1).toList.tail
      UPMbssOutLight(listRun.map(_.relSize),listRun.map(x => x.lastLineProbTraj match {case None => "";case Some(s) => s}))
    }

}

case class UPMbssOut(sizes : List[Double], configurations : List[CfgMbss]) {}
case class UPMbssOutLight(sizes : List[Double], lastLines : List[String] ) {}