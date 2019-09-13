import java.security.KeyStore.TrustedCertificateEntry

//import com.sun.org.apache.bcel.internal.generic.RETURN
//import Simulation
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

case class ResultData(status : Int = 0, errmsg : String = "" , stat_dist : String = null,
                      prob_traj : String = null, traj : String = null, FP : String = null, runlog : String = null) {}

object DataStreamer {

  def buildStreamData(client_data: ClientData, hints: Map[String, Boolean] = null): String = {
    val hexFloat: Boolean = if (hints != null) {hints.getOrElse("hexFloat", false)} else false
    val overRide: Boolean = if (hints != null) {hints.getOrElse("overRide", false)} else false
    val augument: Boolean = if (hints != null) {hints.getOrElse("augument", false)} else false
    val verbose: Boolean = if (hints != null) {hints.getOrElse("verbose", false)} else false
    val flags: Int = 0 | (if (hexFloat) GlCst.HEXFLOAT_FLAG else 0) |
      (if (overRide) GlCst.OVERRIDE_FLAG else 0) | (if (augument) GlCst.AUGMENT_FLAG else 0)

    val header: String = GlCst.MABOSS_MAGIC + "\n" +
      GlCst.PROTOCOL_VERSION + GlCst.PROTOCOL_VERSION_NUMBER + "\n" +
      GlCst.FLAGS + flags.toString + "\n" +
      GlCst.COMMAND + client_data.command + "\n"

    val offsetConfig = client_data.config.length()
    val dataConfig = client_data.config
    val headerConfig  = DataStreamer.add_header(header, GlCst.CONFIGURATION, 0, offsetConfig)

    val offsetConfigNetwork = offsetConfig + client_data.network.length
    val dataConfigNetwork =  dataConfig + client_data.network
    val headerConfigNetwork = DataStreamer.add_header(headerConfig, GlCst.NETWORK, offsetConfig, offsetConfigNetwork)

    if (verbose) {
      print("======= sending header\n"+headerConfigNetwork)
      print("======= sending data[0:200]\n"+ dataConfigNetwork.substring(0, 200), "\n[...]\n"
      )
    }
    headerConfigNetwork + "\n" + dataConfigNetwork
  }

  def parseStreamData(ret_data : String, hints : Map[String,Boolean] = null): ResultData = {
    val verbose: Boolean = if (hints != null) {hints.getOrElse("verbose", false)} else false
    val magic : String = GlCst.RETURN + " " + GlCst.MABOSS_MAGIC
    val magic_len : Int = magic.length
    if (ret_data.substring(0,magic_len) != magic) {
      ResultData(status = 1,errmsg = "magic " + magic + " not found in header")
    }
    else {
      //offset = magic_len
      val pos = ret_data.indexOf("\n\n", magic_len)
      if (pos < 0) {ResultData(status = 2,errmsg = "separator double nl found in header")}
      else {
        //offset += 1
        val header = ret_data.substring(magic_len+1,pos+1)
        val data  = ret_data.substring(pos+2)
        if (verbose) {
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

class MaBoSSClient (host : String = null, port : String = null, maboss_serverInput : String = null) {
  var SERVER_NUM = 1
  val maboss_server = if (maboss_serverInput == null) {
    try {
      sys.env("MABOSS-SERVER")
    } catch {
      case _: Throwable => "MaBoSS-server"
    }
  }
  else maboss_serverInput

  if (host == null) {
    if (port == None) {
      val newPort = "tmp/MaBoSS_pipe_" +
        java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")(0) +
        "_" + SERVER_NUM.toString
    }
    val pidfile = "/tmp/MaBoSS_pidfile_" +
       java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")(0) +
      "_" + SERVER_NUM.toString
    SERVER_NUM += 1
}




