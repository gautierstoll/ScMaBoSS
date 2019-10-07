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

  def upDate(optionResult : Option[Result]) : CfgMbss = {
  optionResult match {
    case None => cfgMbss
    case Some(result) => {

    }
  }
 }

 def run : UPMbssOut = {
   def stepRun(results : List[Result] , step : Int ) : List[Result] = { //careful, list in in reverse order
     step match {
       case s:Int if (s == steps) => results
       case _ => {
         val newSimulation : CfgMbss = upDate(results match {
           case Nil => None : Option[Result]
           case list : List[Result] => Some(list.head)
         })
         val mcli = new MaBoSSClient(port=4291)
         println("Start Simulation of step"+step)
         val result= mcli.run(newSimulation,hints)
         mcli.close()
         stepRun(result :: results,step+1)
       }
     }
   }
   stepRun( Nil : List[Result],steps)
   
 }
}

case class UPMbssOut(sizes : List[Double],  states : List[Map[NetState,Double]], configurations : List[CfgMbss]) {}