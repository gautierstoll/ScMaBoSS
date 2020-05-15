
import java.net.Socket
import java.io._
import java.util._
import java.net._
import java.io.InterruptedIOException

//import ScMaBoSS.{BndMbss, CfgMbss, Hints, MaBoSSClient}
import ScMaBoSS._
//import ScMaBoSS.{BndMbss, CfgMbss, Hints, MaBoSSClient}


val hints : Hints = Hints(check = false,hexfloat = false,augment = true,overRide = false,verbose = false)
val simulation : CfgMbss = CfgMbss.fromFile("Tests2/ToyModel4Sc.cfg", BndMbss.fromFile("Tests2/ToyModel4Sc.bnd"))
val simulationL = simulation.update(scala.collection.immutable.Map("sample_count" -> "100000"))
//val qMb = new QueueMbssClient(port=43291)

// val fRes1 = qMb.sendSimulation("sim1",simulation,hints)
// val fRes2 = qMb.sendSimulation("sim2",simulation,hints)
// val fResL1 = qMb.sendSimulation("simL1",simulationL,hints)
import scala.concurrent.duration._
//scala.concurrent.Await.result(fRes1,10 seconds)


//val oMcli = MaBoSSClient(port=43291)
//println("Start Simulation")
//val result_test = oMcli match {
//  case Some(mcli) => mcli.run(simulation, hints)
//  case None => null
 // could also write val result_test = new Result(mcli,simulation,hints)

//println("Finished simulation")

//val simulation : CfgMbss = CfgMbss.fromFile("Tests/CellFateModel.cfg",
//  BndMbss.fromFile("Tests/CellFateModel.bnd"))

val upMaBoSSTest2 : UPMaBoSS =
  new UPMaBoSS("Tests2/ToyModel4Sc.upp",simulationL,43291,false,true)
val resUP2 : UPMbssOutLight = upMaBoSSTest2.runLight(8)

//val upMaBoSSTest3 : UPMaBoSS =
//  new UPMaBoSS("Tests2/ToyModel4Sc.upp",CfgMbss.fromFile("Tests2/ToyModel4Sc.cfg",BndMbss.fromFile("Tests2/ToyModel4Sc.bnd")),4291,false,true)

//val resUP3 : UPMbssOutLight = UPMaBoSSTest2.runLight(14)

//val probStat = ((new NetState(simulation.extNodeList.zip(false :: true :: false :: true :: Nil).toMap,simulation),.5) ::
//  (new NetState(simulation.extNodeList.zip(true :: true :: false :: true :: Nil).toMap,simulation),.2) ::
//  (new NetState(simulation.extNodeList.zip(true :: true :: true :: true :: Nil).toMap,simulation),.3) :: Nil)
//def fib(an:(Int,Int,Int)) : (Int,Int,Int)= if (an._1 ==1) an else fib(an._1-1,an._3,an._2+an._3)