
case class ClientData(network : String = null, config : String = null, command : String = "Run") {}

case class Hints(check : Boolean = false, hexfloat : Boolean = false, augment : Boolean =  true,
                 overRide : Boolean = false , verbose : Boolean = false) {}

case class ResultData(status : Int = 0, errmsg : String = "" , stat_dist : String = null,
                      prob_traj : String = null, traj : String = null, FP : String = null, runlog : String = null) {}

object Result {

}

class Result ( mbcli : MaBoSSClient, simulation : CfgMbss, hints : Hints) {
  // def mbssStringtoDouble(string: String): Double; scala is supposed to understand the difference
  def doubleToMbssString(double: Double): String = {
    if (hints.hexfloat) java.lang.Double.toHexString(double) else double.toString
  }

  val command : String = if (hints.check) {GlCst.CHECK_COMMAND} else GlCst.RUN_COMMAND
  val clientData : ClientData = ClientData(simulation.bndMbss.bnd,simulation.cfg,command)
  val data : String = DataStreamer.buildStreamData(clientData,hints)
  val outputData : String= mbcli.send(data)
  val parsedResultData : ResultData = DataStreamer.parseStreamData(outputData,hints)

  /**
    * update last probability distribution for UPMaBoSS
     * @param divNode division node
    * @param deathNode death node
    * @return (new_statistical_distribution,normalization_factor)
    */
  def updateLastLine(divNode : String,deathNode : String) : (Map[String,Double],Double) = {
    val nonNormDist = parsedResultData.prob_traj.split("\n").toList.last.
      split("\t").dropWhile("[0-9].*".r.matches(_)).
      sliding(3,3).map(x=>(x(0)->x(1).toDouble)).
      filter(x=> !(x._1.split(" -- ").contains(deathNode))).
      map(x=> {if (x._1.split(" -- ").contains(divNode)) {
        (divNode.r.replaceAllIn((" -- "+divNode).r.replaceAllIn((divNode+" -- ").r.replaceAllIn(x._1,""),""),"<nil>") ->
          x._2*2)} else (x._1->x._2)}).toList
    val normFactor = nonNormDist.map(x=> x._2).sum
    (nonNormDist.map(x=>(x._1, (x._2/normFactor))).toMap,normFactor)
  }
}

