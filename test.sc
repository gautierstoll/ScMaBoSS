import java.security.KeyStore.TrustedCertificateEntry
import Runtime._
import java.net.Socket
import java.io._
import java.util._
import java.net._
import java.io.InterruptedIOException


val mcli = new MaBoSSClient(port=43291)
val simulation : Simulation = new Simulation(bndFile = "cellcycle.bnd",cfgFile = "cellcycle_runcfg.cfg")
val command = GlCst.RUN_COMMAND
val hints : Hints = Hints(check = false,hexfloat = true,augment = true,overRide = false,verbose = false)

println("Start Simulation")
val result= mcli.run(simulation,hints)
println("Finished simluation")
