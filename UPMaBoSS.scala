

object UPMaBoSS {
  def fromFiles(upFile : String,cfg : CfgMbss) : UPMaBoSS = {

  }
}
class UPMaBoSS(val divNode : String, val deathNode : String,val updateVar : List[(String,String)],val seed:Int, val cfgMbss : CfgMbss,val steps : Int) {
  val hints : Hints = Hints(check = false,hexfloat = false,augment = true,overRide = false,verbose = false)
 def upDate(result : Result) : CfgMbss = {
 }
 def run : UPMbssOut = {
   def stepRun(results : List[Result] , step : Int ) : List[Result] = { //careful, list in in reverse order
     step match {
       case s:Int if(s == steps) => results
       case _ => {
         val newSimulation : CfgMbss = upDate(results.head)
         val mcli = new MaBoSSClient(port=4291)
         println("Start Simulation of step"+step)
         val result= mcli.run(newSimulation,hints)
         mcli.close()
         stepRun(result :: results,step+1)
       }}
   }

 }
}

case class UPMbssOut(sizes : List[Double],  states : List[Map[NetState,Double]], configurations : List[CfgMbss]) {}