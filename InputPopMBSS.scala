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
class PopNetState(val stateString: String,val nodeList : List[String]){
 val stringSetArray :  Array[(Set[String],Long)] = stateString.replaceAll("\\s*\\[|\\]\\s*","").
   split(",").map( s => {val keyVal = s.replaceAll("\\{|\\}","").split(":")
  if (keyVal.size < 2) (Set(""),0.toLong) else
  (keyVal(0).split(" -- ").toSet,keyVal(1).toLong)})
 lazy val stateList : List[(NetState,Long)] = stringSetArray.
   map(s => (new NetState(s._1,nodeList),s._2)).toList
 lazy val state : Map[NetState,Long] = stateList.groupBy(_._1).map(x => (x._1 -> x._2.map(_._2).sum))

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
  def activeNodeNb(node : String) : Long = stringSetArray.filter(x => x._1.contains(node)).map(_._2).sum

   // state.filterKeys(ns => ns.activeNodes.contains(node)).values.sum



  /** ratio of cells with active node
    *
    * @param node
    * @return
    */
  def activeNodeRatio(node : String) : Double = this.activeNodeNb(node).toDouble/nbCell


   // this.activeNodeNb(node).toDouble/state.values.sum

 /** number of cells in a given state
   *
   * @param netState network state (can "enclose" network states of the model)
   * @return number of cells
   */
 def stateNb(netState: NetState) : Long = stringSetArray.filter(strSNb =>
  (netState.activeNodes.diff(strSNb._1).isEmpty & netState.inactiveNodes.intersect(strSNb._1).isEmpty)).
   map(_._2).sum



  /* state.filter(netStateNb =>
  (netState.activeNodes.diff(netStateNb._1.activeNodes).isEmpty & netState.inactiveNodes.diff(netStateNb._1.inactiveNodes).isEmpty)).
   values.sum */

 /** ratio in a given state
   *
   * @param netState network state (can "enclose" network states of the model)
   * @return ratio
   */
 def stateRatio(netState: NetState) : Double = this.stateNb(netState).toDouble/nbCell
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

 /** expectation number of a network state
   *
   * @param nState network state (can "enclose" network states of the model)
   * @return expectation value
   */
 def expectNb (nState :NetState) : Double =
  pStMap.map(pStateProb => pStateProb._1.stateNb(nState).toDouble*pStateProb._2).sum

 /** expectation number of an active node
   *
   * @param node node name
   * @return expectation value
   */
 def expectActiveNodeNb (node : String) : Double = {
  this.expectNb(new NetState(Map(node -> true)))
 }

 /** expectation log number of a network state (0 numbers are replaced by 1)
   *
   * @param nState network state (can "enclose" network states of the model)
   * @return expectation value
   */
 def expectLogNb (nState :NetState) : Double =
  pStMap.map(pStateProb => logNoZero(pStateProb._1.stateNb(nState))*pStateProb._2).sum


 /** expectation ratio of a network state
   *
   * @param nState network state (can "enclose" network states of the model)
   * @return expectation value
   */
 def expectRatio (nState : NetState) : Double =
  pStMap.map(pStateProb => (pStateProb._1.stateRatio(nState))*pStateProb._2).sum

 /** expectation ratio of an active node
   *
   * @param node node name
   * @return expectation value
   */
 def expectActiveNodeRatio (node : String) : Double =
  this.expectRatio(new NetState(Map(node -> true)))

 /** expectation of logit ratio can provide a min value (logit(0) -> logit(min)logit(1))
   * a min value is used (logit(0) -> logit(sensit), logit(1) -> logit(1-sensit)
   *
   * @param nState network state (can "enclose" network states of the model)
   * @param sensit if set to zero, sensit is computed from 1/max number of cells in pStateMap
   * @return expectation value
   */
 def expectLogitRatio(nState : NetState, sensit : Double = 0): Double = {
  val sensit4Logit : Double = if (sensit == 0.0) { minSensit } else sensit
  pStMap.map(pStateProb => logitSens(pStateProb._1.stateRatio(nState),sensit4Logit)*pStateProb._2).sum
 }

 /** covariance number of network state pairs
   *
   * @param nState1 network state (can "enclose" network states of the model)
   * @param nState2 network state (can "enclose" network states of the model)
   * @return covariance
   */
 def covNb (nState1 : NetState, nState2 : NetState) : Double = {
  val expNbState1 = expectNb(nState1)
  val expNbState2 = expectNb(nState2)
  pStMap.map(pStatProb =>
   (pStatProb._1.stateNb(nState1).toDouble - expNbState1)*
     (pStatProb._1.stateNb(nState2).toDouble - expNbState2)*
     pStatProb._2).sum
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
  pStMap.map(pStatProb =>
   (logNoZero(pStatProb._1.stateNb(nState1)) - expLogNbState1)*
     (logNoZero(pStatProb._1.stateNb(nState2)) - expLogNbState2)*
     pStatProb._2).sum
 }

 /** covariance ratio of network state pairs
   *
   * @param nState1 network state (can "enclose" network states of the model)
   * @param nState2 network state (can "enclose" network states of the model)
   * @return covariance
   */
 def covRatio (nState1 : NetState, nState2 : NetState) : Double = {
  val expRatioState1 = expectRatio(nState1)
  val expRatioState2 = expectRatio(nState2)
  pStMap.map(pStatProb =>
   (pStatProb._1.stateRatio(nState1) - expRatioState1)*
     (pStatProb._1.stateRatio(nState2) - expRatioState2)*
     pStatProb._2).sum
 }

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
  pStMap.map(pStatProb =>
   (logitSens(pStatProb._1.stateRatio(nState1),sensit4Logit) - expLogitRatioState1)*
     (logitSens(pStatProb._1.stateRatio(nState2),sensit4Logit) - expLogitRatioState2)*
     pStatProb._2).sum


 }
 def probDist[OutType](fPopNetState : (PopNetState => OutType)) : List[(OutType,Double)] =
  pStMap.toList.map(pStProb => (fPopNetState(pStProb._1),pStProb._2)).
    groupBy(_._1).map(x=> (x._1,x._2.map(_._2).sum)).toList

 def probDistNb: List[(Long, Double)] = probDist(pNSt => pNSt.nbCell)
 def probDistNodeNb(node: String): List[(Long, Double)] = probDist(pNSt => pNSt.activeNodeNb(node))
 def probDistNodeRatio(node: String): List[(Double, Double)] = probDist(pNSt => pNSt.activeNodeRatio(node))
 def probDistStateNb(state: NetState): List[(Long, Double)] = probDist(pNSt => pNSt.stateNb(state))
 def probDistStateRatio(state: NetState): List[(Double, Double)] = probDist(pNSt => pNSt.stateRatio(state))
}
