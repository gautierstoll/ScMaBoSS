import java.security.KeyStore.TrustedCertificateEntry
import Runtime._
import java.net.Socket
import java.io._
import java.net._
import java.io.InterruptedIOException


val mcli = new MaBoSSClient(port=43291)
val simulation : Simulation = new Simulation(bndFile = "cellcycle.bnd",cfgFile = "cellcycle_runcfg.cfg")
val command = GlCst.RUN_COMMAND
val hint : Hints = Hints(check = false,hexfloat = true,augment = true,overRide = false,verbose = false)
val clienData : ClientData = ClientData(network = simulation.network,config = simulation.config,command = command)
val data = DataStreamer.buildStreamData(clienData,hint)

val pred : PrintWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mcli.socket.getOutputStream)), true)
val plec : BufferedReader = new BufferedReader(new InputStreamReader(mcli.socket.getInputStream))

pred.print(data)
pred.print(0.toChar)
// val resData = plec.readLine()