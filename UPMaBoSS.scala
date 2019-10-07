import org.apache.commons.lang.ObjectUtils.Null
import scalatags.Text.short.*

object UPMaBoSS {
  def fromFiles(upFile: String, cfg: CfgMbss): UPMaBoSS = {
    val upLines : List[String] = ManageInputFile.file_get_content(upFile).split("\n").toList
    val deathNode : String = upLines.filter(x => "\\s*death\\s*".r.findFirstIn(x).isDefined) match {
      case Null => ""
      case deathList: List[String] => "[\\s;]*".r.replaceAllIn("*\\s*death\\s*=\\s*".r.replaceAllIn(deathList.head,""),"")
    }
    val divisionNode : String = upLines.filter(x => "\\s*division\\s*".r.findFirstIn(x).isDefined) match {
      case Null => ""
      case divisionList: List[String] => "[\\s;]*".r.replaceAllIn("*\\s*division\\s*=\\s*".r.replaceAllIn(divisionList.head,""),"")
    }
    val steps : Int = upLines.filter(x => "\\s*step\\s*".r.findFirstIn(x).isDefined) match {
      case Null => 1
      case stepList: List[String] => "[\\s;]*".r.replaceAllIn("*\\s*division\\s*=\\s*".r.replaceAllIn(stepList.head,""),"").toInt
    }
    val seed : Int = upLines.filter(x => "\\s*seed\\s*".r.findFirstIn(x).isDefined) match {
      case Null => 0
      case seedList: List[String] => "[\\s;]*".r.replaceAllIn("*\\s*division\\s*=\\s*".r.replaceAllIn(seedList.head,""),"").toInt
    }
    val updateVar : List[(String,String)]= upLines.filter(x => "u=".r.findFirstIn(x).isDefined).
      map(x => x.split("\\s*u=\\s*").toList).
      map( x=> ("\\s*".r.replaceAllIn(x.head,""),x.tail.head))

    new UPMaBoSS(divisionNode,deathNode,updateVar,steps,seed,cfg)
  }
}
class UPMaBoSS(val divNode : String, val deathNode : String, val updateVar : List[(String,String)],
               val steps : Int , val seed:Int, val cfgMbss : CfgMbss) {
  val hints : Hints = Hints(check = false,hexfloat = false,augment = true,overRide = false,verbose = false)

  def upDate(optionResult : Option[Result]) : (CfgMbss,Double) = {
  optionResult match {
    case None => cfgMbss
    case Some(result) => {
    val upLastLine = result.updateLastLine(divNode,deathNode)
      (cfgMbss.setInitCond(upLastLine._1).
        ,upLastLine._2)
    }
  }
 }

 def run : UPMbssOut = {
   def stepRun(results : List[Result] , step : Int, ratio : List[Double] ) : (List[Result],List[Double]) = { //careful, list in in reverse order
     step match {
       case s:Int if (s == steps) => (results,upDate(results match {
         case Nil => None : Option[Result]
         case list : List[Result] => Some(list.head)
       })._2 :: ratio)
       case _ => {
         val cfgRatio  = upDate(results match {
           case Nil => None : Option[Result]
           case list : List[Result] => Some(list.head)
         })
         val newSimulation = cfgRatio._1
         val mcli = new MaBoSSClient(port=4291)
         println("Start Simulation of step"+step)
         val result= mcli.run(newSimulation,hints)
         mcli.close()
         stepRun(result :: results,step+1,cfgRatio._2 :: ratio)
       }
     }
   }
   stepRun( Nil : List[Result],steps, 1d :: Nil)

 }
}

case class UPMbssOut(sizes : List[Double],  states : List[Map[NetState,Double]], configurations : List[CfgMbss]) {}