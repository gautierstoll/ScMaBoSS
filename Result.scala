
class Result ( mbcli : MaBoSSClient, simulation : Simulation, hints : Hints) {
  val command : String = if (hints.check) {GlCst.CHECK_COMMAND} else GlCst.RUN_COMMAND
  val clientData : ClientData = ClientData(simulation.network,simulation.config,command)
  val data : String = DataStreamer.buildStreamData(clientData,hints)
  val outputData = mbcli.send(data)
  val parsedResultData = DataStreamer.parseStreamData(outputData,hints)
}
