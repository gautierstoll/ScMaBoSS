package ScMaBoSS

import scala.collection.immutable.List

/** Class for population state, for PopMaBoSS
  *
  * @param stateString string representation of population state
  * @param nodeList list of model nodes, necessary for constructing the set of NetStates
  *                 if nodeList is smaller than the one used for generating stateString, cell numbers are summed up
  */
class PopNetState(val stateString: String,nodeList : List[String]){
 val state : Map[NetState,Long] = stateString.replaceAll("\\s*\\[|\\]\\s*","").
   split(",").map( s => {val keyVal = s.replaceAll("\\{|\\}","").split(":")
   (new NetState(keyVal(0),nodeList),keyVal(1).toLong)}).groupBy(_._1).map(x => (x._1 -> x._2.map(_._2).sum))

 /** number of cells
   *
   */
 val nbCell: Long = state.values.sum

  override def toString: String = stateString

  /** number of cells with active node
    *
    * @param node
    * @return
    */
  def activeNodeNb(node : String) : Long = state.filterKeys(ns => ns.activeNodes.contains(node)).values.sum

  /** ratio of cells with active node
    *
    * @param node
    * @return
    */
  def activeNodeRatio(node : String) : Double = this.activeNodeNb(node).toDouble/state.values.sum
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
   * @param nState network state
   * @return expectation value
   */
 def expectNb (nState :NetState) : Double =
  pStMap.map(pStatProb => pStatProb._1.state.getOrElse(nState,0.toLong).toDouble*pStatProb._2).sum

 /** expectation number of an active node
   *
   * @param node node name
   * @return expectation value
   */
 def expectActiveNodeNb (node : String) : Double =
  pStMap.map(pStatProb => pStatProb._1.activeNodeNb(node).toDouble*pStatProb._2).sum

 /** expectation ratio of a network state
   *
   * @param nState network state
   * @return expectation value
   */
 def expectRatio (nState : NetState) : Double =
  pStMap.map(pStatProb =>
   pStatProb._1.state.getOrElse(nState,0.toLong).toDouble*pStatProb._2/pStatProb._1.state.values.sum).sum

 /** expectation ratio of an active node
   *
   * @param node node name
   * @return expectation value
   */
 def expectActiveNodeRatio (node : String) : Double =
  pStMap.map(pStatProb => pStatProb._1.activeNodeNb(node).toDouble*pStatProb._2/pStatProb._1.state.values.sum).sum

 /** covariance number of network state pairs
   *
   * @param nState1 network state
   * @param nState2 network state
   * @return covariance
   */
 def covNb (nState1 : NetState, nState2 : NetState) : Double = {
  val expNbState1 = expectNb(nState1)
  val expNbState2 = expectNb(nState2)
  pStMap.map(pStatProb =>
   (pStatProb._1.state.getOrElse(nState1,0.toLong).toDouble - expNbState1)*
     (pStatProb._1.state.getOrElse(nState2,0.toLong).toDouble - expNbState2)*
     pStatProb._2).sum
 }

 /** covariance number of active node pairs
   *
   * @param node1 node name
   * @param node2 node name
   * @return covariance
   */
 def covActiveNodeNb (node1 : String, node2 : String) : Double = {
  val expNbNode1 = expectActiveNodeNb(node1)
  val expNbNode2 = expectActiveNodeNb(node2)
  pStMap.map(pStatProb =>
   (pStatProb._1.activeNodeNb(node1).toDouble - expNbNode1)*
     (pStatProb._1.activeNodeNb(node2).toDouble - expNbNode2)*
     pStatProb._2).sum
 }

 /** covariance ratio of network state pairs
   *
   * @param nState1 network state
   * @param nState2 network state
   * @return covariance
   */
 def covRatio (nState1 : NetState, nState2 : NetState) : Double = {
  val expRatioState1 = expectRatio(nState1)
  val expRatioState2 = expectRatio(nState2)
  pStMap.map(pStatProb =>
   (pStatProb._1.state.getOrElse(nState1,0.toLong).toDouble/pStatProb._1.state.values.sum - expRatioState1)*
     (pStatProb._1.state.getOrElse(nState2,0.toLong).toDouble/pStatProb._1.state.values.sum - expRatioState2)*
     pStatProb._2).sum
 }

 /** covariance ratio of active node pairs
   *
   * @param node1 node name
   * @param node2 node name
   * @return covariance
   */
 def covActiveNodeRatio (node1 : String, node2 : String) : Double = {
  val expRatioNode1 = expectActiveNodeRatio(node1)
  val expRatioNode2 = expectActiveNodeRatio(node2)
  pStMap.map(pStatProb =>
   (pStatProb._1.activeNodeNb(node1).toDouble/pStatProb._1.state.values.sum - expRatioNode1)*
     (pStatProb._1.activeNodeNb(node2).toDouble/pStatProb._1.state.values.sum - expRatioNode2)*
     pStatProb._2).sum
 }
}
