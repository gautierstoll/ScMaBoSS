
class Result ( mbcli : MaBoSSClient, simulation : CfgMbss, hints : Hints) {
  val command : String = if (hints.check) {GlCst.CHECK_COMMAND} else GlCst.RUN_COMMAND
  val clientData : ClientData = ClientData(simulation.bndMbss.bnd,simulation.cfg,command)
  val data : String = DataStreamer.buildStreamData(clientData,hints)
  val outputData : String= mbcli.send(data)
  val parsedResultData : ResultData = DataStreamer.parseStreamData(outputData,hints)
}
