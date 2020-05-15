package ScMaBoSS

import java.security.KeyStore.TrustedCertificateEntry
import Runtime._
import java.net.Socket
import java.io._
import java.util._
import java.net._
import java.io.InterruptedIOException

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.BufferedSource
//import scala.collection.JavaConversions._


import scala.concurrent.{Await, ExecutionContext, Future, Promise, duration}
import scala.util.{Success, Failure}
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import ScMaBoSS.{CfgMbss, ClientData, Hints, ResultData}

import scala.sys.process

/**Constants for MaBoSS server protocol 1.0
  *
  */
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

/**Methods for parsing input and output data of MaBoSS server
  *
  */
object DataStreamer {
  /** build data to be sent to MaBoSS server
    *
    * @param client_data
    * @param hints
    * @return
    */
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

  /** parse data received by MaBoSS server
    *
    * @param ret_data
    * @param verbose
    * @return
    */
  def parseStreamData(ret_data : String, verbose : Boolean): ResultData = {
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
        if (verbose) {
          print("======= receiving header \n" + header)
          print("======= receiving data[0:200]\n" + data.substring(0, 200) + "\n[...]\n")
        }
        var resStatus: Int = 0
        var resErrmsg: String = ""
        var resStat_dist: String = null
        var resProb_traj: String = null
        var resTraj: String = null
        var resFP: String = null
        var resRunlog: String = null
        var oposLoop: Int = 0
        var posLoop: Int = 0
        var loop: Boolean = true
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

/** Socket communication with MaBoSS server
  *
  * @param socket
  */
class MaBoSSClient (val socket : java.net.Socket) {
  private val bos : BufferedOutputStream = new BufferedOutputStream(socket.getOutputStream)
  private val scannerBis : Scanner = new Scanner(new BufferedInputStream(socket.getInputStream)).useDelimiter(0.toChar.toString)
  def send(inputData: String,tOut : Option[Int] = None):Option[String] =  {
    tOut match {
      case None => {}
      case Some(i) => socket.setSoTimeout(i)}
    bos.write(inputData.getBytes())
    bos.write(0.toChar)
    try bos.flush() catch {
      case e: Throwable => {
        System.err.print("IOerror by flushing buffer to MaBoSS server, socket may be closed ")
        None
      }
    }
    try Some(scannerBis.next()) catch {
      case e: SocketTimeoutException => println("Timeout server reached"); None
      case e: Throwable => System.err.print(e.toString);None
    }
  }
  def close(): Unit = {socket.close()}

  /** Run a MaBoSS simulation and return a Result. Socket is closed after the run.
    *
    * @param simulation
    * @param hints
    * @return
    */
  def run(simulation: CfgMbss,hints: Hints ): Option[Result] =
  {Result.fromInputsMBSS(this,simulation,hints)}
}

/** Factory object
  *
  */
object MaBoSSClient {
  /** Construct socket from server host and port
    *
    * @param host
    * @param port
    * @return
    */
  def apply(host: String = "localhost", port: Int): Option[MaBoSSClient] = {
      try {
        Some(new MaBoSSClient(new java.net.Socket(host, port)))
      } catch {
        //case e: Throwable => {println(e);System.err.print("error trying to connect to port " + port + " and host "+ host);sys.exit(1)}
        case e: Exception => {
          println(e)
          System.err.println("error trying to connect to port " + port + " and host "+ host)
          None
        }
      }
  }
}

/**
  * MaBoSS client queue
  * @param hostName
  * @param port
  */
class QueueMbssClient(val hostName : String = "localhost", port : Int) {
  private val queueSim = scala.collection.mutable.ListBuffer[(String, Future[Option[Result]])]()
  private val logStr = new scala.collection.mutable.StringBuilder
  /**
    *
    * @param name name of the job
    * @param cfgMaBoSS
    * @param hints
    * @return
    */
  def sendSimulation(name: String, cfgMaBoSS: CfgMbss, hints: Hints): Future[Option[Result]] = this.synchronized {
    val precFuture: Option[(String, Future[Option[Result]])] = queueSim.lastOption
    val futureMbSS: Future[Option[Result]] = Future {
      precFuture match {
        case Some((s, f)) => {
          logStr.++=(name +  " wait until "+ s +" is done\n")
          Await.result(f, Duration.Inf)
          queueSim.remove(0)
        }
        case None => {}
      }
      logStr ++= ("Send "+ name + " to MaBoSS Server\n")
      MaBoSSClient(hostName, port) match {
        case Some(mcli) => mcli.run(cfgMaBoSS, hints)
        case _ => None
      }
    }
    queueSim.+=((name, futureMbSS))
    futureMbSS
  }
  /** get the list of current job, first one is on MaBoSS server (or finished)
    *
    * @return
    */
  def getQueue: scala.collection.immutable.List[String]= queueSim.map(_._1).toList

  /** get the log of the queue
    *
    * @return
    */
  def getLog: String = logStr.toString
}
