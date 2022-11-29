package ScMaBoSS

import scala.annotation.tailrec
import scala.collection.immutable.List
import scala.math._

/** Class for population state, for PopMaBoSS
  *
  * @param stateString string representation of population state
  * @param nodeList list of model nodes, necessary for constructing the set of NetStates
  *                 if nodeList is smaller than the one used for generating stateString, cell numbers are summed up
  */
class PopNetState(val stateString: String,val nodeList : List[String]) {
 val stringSetArray: Array[(Set[String], Long)] = stateString.replaceAll("\\s*\\[|\\]\\s*", "").
   split(",").map(s => {
  val keyVal = s.replaceAll("\\{|\\}", "").split(":")
  if (keyVal.size < 2) (Set(""), 0.toLong) else
   (keyVal(0).split(" -- ").toSet, keyVal(1).toLong)
 })
 lazy val stateList: List[(NetState, Long)] = stringSetArray.
   map(s => (new NetState(s._1, nodeList), s._2)).toList
 lazy val state: Map[NetState, Long] = stateList.groupBy(_._1).map(x => (x._1 -> x._2.map(_._2).sum))

 /** number of cells
   *
   */
 lazy val nbCell: Long = stringSetArray.map(_._2).sum

 override def toString: String = stateString

 /** number of cells with active node
   *
   * @param node
   * @return
   */
 def activeNodeNb(node: String): Long = stringSetArray.filter(x => x._1.contains(node)).map(_._2).sum

 /** ratio of cells with active node
   *
   * @param node
   * @return
   */
 def activeNodeRatio(node: String): Option[Double] =
  nbCell match {
   case 0 => None
   case n: Long => Some(this.activeNodeNb(node).toDouble / n)
  }

 /** number of cells in a given state
   *
   * @param netState network state (can "enclose" network states of the model)
   * @return number of cells
   */
 def stateNb(netState: NetState): Long = stringSetArray.filter(strSNb =>
  (netState.activeNodes.diff(strSNb._1).isEmpty & netState.inactiveNodes.intersect(strSNb._1).isEmpty)).
   map(_._2).sum

 /** ratio in a given state
   *
   * @param netState network state (can "enclose" network states of the model)
   * @return ratio
   */
 def stateRatio(netState: NetState): Option[Double] = nbCell match {
  case 0 => None
  case n => Some(this.stateNb(netState).toDouble / n)
 }
}

/** Sub class of BndMbss, for PopMaBoSS
  *
  * @param pbnd
  */
class PBndMBSS(val pbnd : String) extends BndMbss(pbnd)

/** sub class of CfgMbss, for PopMaBoSS
  *
  * @param pbnd
  * @param pcfg
  */
class PCfgMBSS(val pbnd : PBndMBSS,val pcfg : String) extends CfgMbss(pbnd,pcfg)

/** class for probability distribution of population states
  *
  * @param inputMap map of string representation of population state and probability
  * @param listNodes list od nodes
  */
class popStateDist(val inputMap : Map[String,Double],val listNodes: List[String]) {
 /** map between pop net state and probability (lazy)
   *
   */
 lazy val pStMap: Map[PopNetState, Double] = inputMap.map(stProb => (new PopNetState(stProb._1, listNodes), stProb._2))
private def logNoZero(x:Double) : Double = if (x <= 0.0) {0} else log(x)
 lazy private val minSensit:Double = 1/pStMap.keys.map(_.nbCell).max.toDouble // minimum sensibility for ratio
private def logitSens(p:Double,sensit:Double) : Double = p match { // logit with minimum sensitivity parameter
 case x if (x <= 0.0)|(x >= 1.0) => log(sensit/(1-sensit))
 case _ => log(p/(1-p))
 }
 /** Compute the probability to detect a node, above a minimum number of cells
   *
   * @param node
   * @param minCellNb
   * @return
   */
 def probDetectNode(node: String, minCellNb: Long): Double = inputMap.filter(pStateProb => pStateProb._1.contains(node)).
   filter(pStateProb => new PopNetState(pStateProb._1, listNodes).state.
     filter(nStateNb => nStateNb._1.toString.contains(node)).values.sum >= minCellNb)
   .values.sum

 /** Compute the probability to detect a node, above a minimum proportion of cells
   *
   * @param node
   * @param minCellProp
   * @return
   */
 def probDetectNode(node: String, minCellProp: Double): Double =
  inputMap.filter(pStateProb => pStateProb._1.contains(node)).
    filter({ pStateProb =>
     val pState = new PopNetState(pStateProb._1, listNodes)
     pState.state.filter(nStateNb => nStateNb._1.toString.contains(node)).values.sum.toDouble / pState.nbCell >= minCellProp
    })
    .values.sum

 def probDist[OutType](fPopNetState : (PopNetState => Option[OutType])) : List[(OutType,Double)] = {
  val nonNormalizedDist = pStMap.toList.map(pStProb => (fPopNetState(pStProb._1), pStProb._2)).
    flatMap(optOutProb => optOutProb._1 match {
     case Some(out) => Some(out, optOutProb._2)
     case None => None
    }).groupBy(_._1).map(x=> (x._1,x._2.map(_._2).sum)).toList
  val normFactor = nonNormalizedDist.map(_._2).sum
  nonNormalizedDist.map(x => (x._1,x._2/normFactor))
 }

 def probDistNb: List[(Long, Double)] = probDist(pNSt => Some(pNSt.nbCell))

 def probDistNodeNb(node: String): List[(Long , Double)] = probDist(pNSt => Some(pNSt.activeNodeNb(node)))
 def probDistNodeNb(nodes: List[String]): List[(List[Long] , Double)] =
  probDist(pNSt => Some(nodes.map(x => pNSt.activeNodeNb(x))))
 def probDistNodeRatio(node: String): List[(Double, Double)] = probDist(pNSt => pNSt.activeNodeRatio(node))
 def probDistNodeRatio(nodes: List[String]): List[(List[Double], Double)] =
  probDist(pNSt => {
   val listOpt = nodes.map(x => pNSt.activeNodeRatio(x))
   if (listOpt.contains(None)) {None} else Some(listOpt.flatten)})

 def probDistStateNb(state: NetState): List[(Long, Double)] = probDist(pNSt => Some(pNSt.stateNb(state)))
 def probDistStateNb(states: List[NetState]): List[(List[Long], Double)] =
  probDist(pNSt => Some(states.map(x => pNSt.stateNb(x))))

 def probDistStateRatio(state: NetState): List[(Double, Double)] = probDist(pNSt => pNSt.stateRatio(state))
 def probDistStateRatio(states: List[NetState]): List[(List[Double], Double)] =
  probDist(pNSt => {
   val listOpt = states.map(x => pNSt.stateRatio(x))
   if (listOpt.contains(None)) {None} else {Some(listOpt.flatten)}})


 /** expectation number of a network state
   *
   * @param nState network state (can "enclose" network states of the model)
   * @return expectation value
   */
 def expectNb (nState :NetState) : Double =
  probDistStateNb(nState).map(x => x._1*x._2).sum

 /** expectation number of an active node
   *
   * @param node node name
   * @return expectation value
   */
 def expectActiveNodeNb (node : String) : Double =
 probDistNodeNb(node).map(x => x._1*x._2).sum

 /** expectation log number of a network state (0 numbers are replaced by 1)
   *
   * @param nState network state (can "enclose" network states of the model)
   * @return expectation value
   */
 def expectLogNb (nState :NetState) : Double =
  probDistStateNb(nState).map(x => logNoZero(x._1)*x._2).sum

 /** expectation ratio of a network state
   *
   * @param nState network state (can "enclose" network states of the model)
   * @return expectation value
   */
 def expectRatio (nState : NetState) : Double =
  probDistStateRatio(nState).map(x => x._1*x._2).sum

 /** expectation ratio of an active node
   *
   * @param node node name
   * @return expectation value
   */
 def expectActiveNodeRatio (node : String) : Double =
  probDistNodeRatio(node).map(x => x._1*x._2).sum

 /** expectation of logit ratio can provide a min value (logit(0) -> logit(min)logit(1))
   * a min value is used (logit(0) -> logit(sensit), logit(1) -> logit(1-sensit)
   *
   * @param nState network state (can "enclose" network states of the model)
   * @param sensit if set to zero, sensit is computed from 1/max number of cells in pStateMap
   * @return expectation value
   */
 def expectLogitRatio(nState : NetState, sensit : Double = 0): Double = {
  val sensit4Logit : Double = if (sensit == 0.0) { minSensit } else sensit
  probDistStateRatio(nState).map(x => logitSens(x._1,sensit4Logit)*x._2).sum
 }

 /** covariance number of network state pairs
   *
   * @param nState1 network state (can "enclose" network states of the model)
   * @param nState2 network state (can "enclose" network states of the model)
   * @return covariance
   */
 def covNb(nState1: NetState, nState2: NetState): Double = {
  val expNbState1 = expectNb(nState1)
  val expNbState2 = expectNb(nState2)
  probDistStateNb(List(nState1, nState2)).map(statesProb =>
   (statesProb._1.head.toDouble - expNbState1) * (statesProb._1.tail.head.toDouble - expNbState2) * statesProb._2).sum
 }

 /** covariance number of active node pairs
   *
   * @param node1 node name
   * @param node2 node name
   * @return covariance
   */
 def covActiveNodeNb (node1 : String, node2 : String) : Double = {
  covNb(new NetState(Map(node1 -> true)),new NetState(Map(node2 -> true)))
 }

 /** covariance log number of network state pairs (0 numbers are replaced by 1)
   *
    * @param nState1 network state (can "enclose" network states of the model)
   * @param nState2 network state (can "enclose" network states of the model)
   * @return covariance
   */
 def covLogNb (nState1 : NetState, nState2 : NetState) : Double = {
  val expLogNbState1 = expectLogNb(nState1)
  val expLogNbState2 = expectLogNb(nState2)
  probDistStateNb(List(nState1, nState2)).map(statesProb =>
   (logNoZero(statesProb._1.head.toDouble) - expLogNbState1) * (logNoZero(statesProb._1.tail.head.toDouble) - expLogNbState2) * statesProb._2).sum}


 /** covariance ratio of network state pairs
   *
   * @param nState1 network state (can "enclose" network states of the model)
   * @param nState2 network state (can "enclose" network states of the model)
   * @return covariance
   */
 def covRatio (nState1 : NetState, nState2 : NetState) : Double = {
  val expRatioState1 = expectRatio(nState1)
  val expRatioState2 = expectRatio(nState2)
  probDistStateRatio(List(nState1,nState2)).map(ratiosProb =>
   (ratiosProb._1.head - expRatioState1)*(ratiosProb._1.tail.head-expRatioState2)*ratiosProb._2).sum}

 /** covariance ratio of active node pairs
   *
   * @param node1 node name
   * @param node2 node name
   * @return covariance
   */
 def covActiveNodeRatio (node1 : String, node2 : String) : Double = {
  covRatio(new NetState(Map(node1 -> true)),new NetState(Map(node1 -> true)))
 }

 /** covariance logit ratio of active node pairs
   * a min value is used (logit(0) -> logit(sensit), logit(1) -> logit(1-sensit)
   *
   * @param nState1
   * @param nState2
   * @param sensit if set to zero, sensit is computed from 1/max number of cells in pStateMap
   * @return
   */
 def covLogitRatio (nState1 : NetState, nState2 : NetState,sensit : Double = 0) : Double = {
  val sensit4Logit : Double = if (sensit == 0.0) { minSensit } else sensit
  val expLogitRatioState1 = expectLogitRatio(nState1,sensit)
  val expLogitRatioState2 = expectLogitRatio(nState2,sensit)
  probDistStateRatio(List(nState1,nState2)).map(ratiosProb =>
   (logitSens(ratiosProb._1.head,sensit4Logit) - expLogitRatioState1) *
     (logitSens(ratiosProb._1.tail.head,sensit4Logit) - expLogitRatioState2)*ratiosProb._2).sum}

}
