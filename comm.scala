import java.security.KeyStore.TrustedCertificateEntry
import Runtime._
import java.net.Socket
import java.io._
import java.util._
import java.net._
import java.io.InterruptedIOException
import scala.sys.process

object GlCst {
  val PROTOCOL_VERSION_NUMBER = "1.0"
  val MABOSS_MAGIC = "MaBoSS-2.0"
  val PROTOCOL_VERSION = "Protocol-Version:"
  val FLAGS = "Flags:"
  val HEXFLOAT_FLAG: Int = 0x1
  val OVERRIDE_FLAG: Int = 0x2
  val AUGMENT_FLAG: Int = 0x4
  val COMMAND = "Command:"
  val RUN_COMMAND = "run"
  val CHECK_COMMAND = "check"
  val PARSE_COMMAND = "parse"
  val NETWORK = "Network:"
  val CONFIGURATION = "Configuration:"
  val CONFIGURATION_EXPRESSIONS = "Configuration-Expressions:"
  val CONFIGURATION_VARIABLES = "Configuration-Variables:"
  val RETURN = "RETURN"
  val STATUS = "Status:"
  val ERROR_MESSAGE = "Error-Message:"
  val STATIONARY_DISTRIBUTION = "Stationary-Distribution:"
  val TRAJECTORY_PROBABILITY = "Trajectory-Probability:"
  val TRAJECTORIES = "Trajectories:"
  val FIXED_POINTS = "Fixed-Points:"
  val RUN_LOG = "Run-Log:"
}
case class ClientData(network : String = null, config : String = null, command : String = "Run") {}

case class Hints(check : Boolean = false, hexfloat : Boolean = false, augment : Boolean =  true,
                 overRide : Boolean = false , verbose : Boolean = false) {}

case class ResultData(status : Int = 0, errmsg : String = "" , stat_dist : String = null,
                      prob_traj : String = null, traj : String = null, FP : String = null, runlog : String = null) {}

object DataStreamer {

  def buildStreamData(client_data: ClientData, hints: Hints = null): String = {

    val flags: Int = 0 | (if (hints.hexfloat) GlCst.HEXFLOAT_FLAG else 0) |
      (if (hints.overRide) GlCst.OVERRIDE_FLAG else 0) | (if (hints.augment) GlCst.AUGMENT_FLAG else 0)

    val header: String = GlCst.MABOSS_MAGIC + "\n" + GlCst.PROTOCOL_VERSION + GlCst.PROTOCOL_VERSION_NUMBER + "\n" +
      GlCst.FLAGS + flags.toString + "\n" + GlCst.COMMAND + client_data.command + "\n"

    val offsetConfig = client_data.config.length()+1
    val dataConfig = client_data.config
    val headerConfig  = DataStreamer.add_header(header, GlCst.CONFIGURATION, 0, offsetConfig)
    val offsetConfigNetwork = offsetConfig + client_data.network.length+1
    val dataConfigNetwork =  dataConfig + "\n"+client_data.network
    val headerConfigNetwork = DataStreamer.add_header(headerConfig, GlCst.NETWORK, offsetConfig, offsetConfigNetwork)
    if (hints.verbose) {
      print("======= sending header\n"+headerConfigNetwork)
      print("======= sending data[0:200]\n"+ dataConfigNetwork.substring(0, 200), "\n[...]\n")
    }
    headerConfigNetwork + "\n" + dataConfigNetwork
  }

  def parseStreamData(ret_data : String, hints : Hints): ResultData = {
    val magic : String = GlCst.RETURN + " " + GlCst.MABOSS_MAGIC
    val magic_len : Int = magic.length
    if (ret_data.substring(0,magic_len) != magic) {
      ResultData(status = 1,errmsg = "magic " + magic + " not found in header")}
    else {
      val pos = ret_data.indexOf("\n\n", magic_len)
      if (pos < 0) {ResultData(status = 2,errmsg = "separator double nl found in header")}
      else {
        val header = ret_data.substring(magic_len+1,pos+1)
        val data  = ret_data.substring(pos+2)
        if (hints.verbose) {
          print("======= receiving header \n" + header)
          print("======= receiving data[0:200]\n" + data.substring(0, 200) + "\n[...]\n")
        }
        var resStatus : Int = 0
        var resErrmsg : String = ""
        var resStat_dist : String = null
        var resProb_traj : String = null
        var resTraj : String = null
        var resFP : String = null
        var resRunlog : String = null
        var oposLoop : Int = 0
        var posLoop : Int = 0
        var loop : Boolean = true
        while(loop)
        {
          posLoop = header.indexOf(":",oposLoop)
          if (posLoop<0) {loop = false}
          else {
            val directive = header.substring(oposLoop,posLoop+1)
            oposLoop = posLoop+1
            posLoop = header.indexOf("\n", oposLoop)
            if (posLoop < 0) {
              resErrmsg = "newline not found in header after directive " + directive
              resStatus = 3
              loop = false
            }
            else {
              val value = header.substring(oposLoop,posLoop)
              oposLoop = posLoop+1
              val pos2 = value.indexOf("-")
              (directive,pos2) match {
                case (GlCst.STATUS,_) => {resStatus = value.toInt}
                case (GlCst.ERROR_MESSAGE,_) => {resErrmsg = value}
                case (_,x) if (x<0) => {
                  resErrmsg =  "dash - not found in value " + value + " after directive " + directive
                  resStatus = 3}
                case (_,_) => {
                  val fromDataIndex = value.substring(0,pos2).toInt
                  val toDataIndex = value.substring(pos2+1).toInt
                  directive match {
                    case GlCst.STATIONARY_DISTRIBUTION => {resStat_dist = data.substring(fromDataIndex,toDataIndex+1)}
                    case GlCst.TRAJECTORY_PROBABILITY => {resProb_traj = data.substring(fromDataIndex,toDataIndex+1)}
                    case GlCst.TRAJECTORIES => {resTraj = data.substring(fromDataIndex,toDataIndex+1)}
                    case GlCst.FIXED_POINTS => {resFP = data.substring(fromDataIndex,toDataIndex+1)}
                    case GlCst.RUN_LOG => {resRunlog = data.substring(fromDataIndex,toDataIndex+1)}
                    case _ => {resErrmsg = "unknown directive " + directive;resStatus=4}
                  }
                }
              }
            }
          }
        }
        ResultData(resStatus,resErrmsg,resStat_dist,resProb_traj,resTraj,resFP,resRunlog)
      }
    }
  }
  def add_header(header: String, directive: String, o_offset: Int, offset: Int) : String = {
    header + (if (o_offset != offset) {directive + o_offset.toString +"-" + (offset - 1).toString + "\n"} else{""})
  }
}

class MaBoSSClient (host : String = "localhost", port : Int) {
  val socket : Socket =
    try {
      new Socket("localhost",port)
    }
    catch {
      case e: Throwable => {System.err.print("error trying to connect to port " + port + " and host "+ host);sys.exit(1)}
    }
  def send(inputData : String):String =  {
    val bos : BufferedOutputStream = new BufferedOutputStream(socket.getOutputStream())
    bos.write(inputData.getBytes())
    bos.write(0.toChar)
    try (bos.flush()) catch {
      case e:Throwable => {System.err.print("IOerror by flushing buffer to MaBoSS server");sys.exit(1)}
    }
    val scannerBis : Scanner = new Scanner(new BufferedInputStream(socket.getInputStream())).useDelimiter(0.toChar.toString)
      scannerBis.next()
  }
  def run(simulation : CfgMbss,hints : Hints ) : Result =
  {new Result(this,simulation,hints)}
  def close() = {socket.close()}
}
