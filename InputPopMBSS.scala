package ScMaBoSS

import scala.collection.immutable.List

/** Class for population state, for PopMaBoSS
  *
  * @param stateString string representation of population state
  * @param nodeList list of model nodes, necessary for constructing the set of NetStates
  */
class PopNetState(val stateString: String,nodeList : List[String]){
 val state : Map[NetState,Long] = stateString.replaceAll("\\s*\\[|\\]\\s*","").
   split(",").map( s => {val keyVal = s.replaceAll("\\{|\\}","").split(":")
   (new NetState(keyVal(0),nodeList),keyVal(1).toLong)}).toMap

  override def toString: String = stateString

  /** number of cells with active node
    *
    * @param node
    * @return
    */
  def activeNodePop(node : String) : Long = state.filterKeys(ns => ns.activeNodes.contains(node)).values.sum

  /** ratio of cells with active node
    *
    * @param node
    * @return
    */
  def activeNodeRatio(node : String) : Double = this.activeNodePop(node).toDouble/state.values.sum
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

