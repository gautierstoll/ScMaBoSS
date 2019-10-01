import java.security.KeyStore.TrustedCertificateEntry
import Runtime._
import java.net.Socket
import java.io._
import java.util._
import java.net._
import java.io.InterruptedIOException


val mcli = new MaBoSSClient(port=4291)
val simulation : CfgMbss = CfgMbss.fromFile("p53_Mdm2_runcfg.cfg", BndMbss.fromFile("p53_Mdm2.bnd"))
val command = GlCst.RUN_COMMAND
val hints : Hints = Hints(check = false,hexfloat = true,augment = true,overRide = false,verbose = false)

println("Start Simulation")
val result_p53= mcli.run(simulation,hints)
println("Finished simluation")
