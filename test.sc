import java.security.KeyStore.TrustedCertificateEntry
import Runtime._
import java.net.Socket
import java.io._
import java.util._
import java.net._
import java.io.InterruptedIOException

import ScMaBoSS.{BndMbss, CfgMbss, Hints, MaBoSSClient}

import scala.collection.immutable.Map

val hints : Hints = Hints(check = false,hexfloat = false,augment = true,overRide = false,verbose = false)
val simulation : CfgMbss = CfgMbss.fromFile("cellcycle_runcfg.cfg", BndMbss.fromFile("cellcycle.bnd"))

val mcli = new MaBoSSClient(port=4291)
println("Start Simulation")
val result_p53= mcli.run(simulation,hints)
mcli.close()
println("Finished simulation")

//val simulation : CfgMbss = CfgMbss.fromFile("Tests/CellFateModel.cfg",
//  BndMbss.fromFile("Tests/CellFateModel.bnd"))

//val upSimulation : UPMaBoSS = UPMaBoSS.fromFiles("Tests/CellFate.upp",simulation)



//val probStat = ((new NetState(simulation.extNodeList.zip(false :: true :: false :: true :: Nil).toMap,simulation),.5) ::
//  (new NetState(simulation.extNodeList.zip(true :: true :: false :: true :: Nil).toMap,simulation),.2) ::
//  (new NetState(simulation.extNodeList.zip(true :: true :: true :: true :: Nil).toMap,simulation),.3) :: Nil)


