
class Result ( mbcli : MaBoSSClient, simulation : Simulation, hints : Hints) {

  val command : String = if (hints.check == null) {GlCst.RUN_COMMAND} else GlCst.CHECK_COMMAND
  val clientData : ClientData = ClientData(simulation.network,simulation.config,command)
  val data : String = DataStreamer.buildStreamData(clientData,hints)
val outputData = mbcli.send(data)
}
