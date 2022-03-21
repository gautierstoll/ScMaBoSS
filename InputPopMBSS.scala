package ScMaBoSS

import scala.collection.immutable.List


class PopNetState(val stateString: String,nodeList : List[String]){
 val state : Map[NetState,Long] = stateString.replaceAll("\\s*\\[|\\]\\s*","").
   split(",").map( s => {val keyVal = s.replaceAll("\\{|\\}","").split(":")
   (new NetState(keyVal(0),nodeList),keyVal(1).toLong)}).toMap
}

class PBndMBSS(val pbnd : String) extends BndMbss(pbnd)

class PCfgMBSS(val pbnd : PBndMBSS,val pcfg : String) extends CfgMbss(pbnd,pcfg)

