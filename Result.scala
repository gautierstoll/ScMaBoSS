
class Result ( mbcli : MaBoSSClient, simulation : Simulation, hints : Map[String,Boolean]) {

  val command : String = if (hints == null) {GlCst.RUN_COMMAND} else
      {if (hints.getOrElse("check", false)){GlCst.CHECK_COMMAND} else GlCst.RUN_COMMAND}
  val clientData : ClientData = ClientData(simulation.network,simulation.config,command)
  val data : String = DataStreamer.buildStreamData(clientData,hints)
val outputData = mbcli.send(data)
}
