package ScMaBoSS

import java.sql.Time

import scala.collection.immutable.List

/** Parsed results of PopMaBoSS
  *
  * @param filenamePop
  * @param filenameSimplPop
  * @param listNodes
  */
class PResultFromFile(filenamePop: String,filenameSimplPop:String,val listNodes: List[String]){
  private val splittedSimpleFile: List[String] = ManageInputFile.file_get_content(filenameSimplPop).split("\n").toList
  private val indexOfPop: Int = splittedSimpleFile.head.split("\t").indexOf("Pop")
  private val zippedLists: (List[(Double, Double)], List[(Double, Double, Double)], List[(Double, Map[String, Double])])  = splittedSimpleFile.tail.map(line => {
    val splittedLine : Array[String] = line.split("\t")
    ((splittedLine(1).toDouble,splittedLine(3).toDouble),
      (splittedLine(indexOfPop).toDouble,splittedLine(indexOfPop+1).toDouble,splittedLine(indexOfPop+2).toDouble),
    (splittedLine(0).toDouble,splittedLine.drop(indexOfPop+3).sliding(2,2).map(x=> (x(0) -> x(1).toDouble)).toMap))}).unzip3

  private val ListTuple :
  ((List[Double],List[Double]),(List[Double],List[Double],List[Double]),(List[Double],List[Map[String,Double]])) =
    (zippedLists._1.unzip,zippedLists._2.unzip3,zippedLists._3.unzip)

  val TH: List[Double] = ListTuple._1._1
  val H: List[Double] = ListTuple._1._2
  val Pop: List[Double] = ListTuple._2._1
  val VarPop: List[Double] = ListTuple._2._2
  val HPop: List[Double] = ListTuple._2._3
  val Time: List[Double] = ListTuple._3._1
  val StateProb: List[Map[String, Double]] = ListTuple._3._2

  lazy val PopStateProb : List[Map[String, Double]] = ManageInputFile.file_get_content(filenamePop).split("\n").toList.tail.
    map(line => line.split("\t").drop(indexOfPop).sliding(2,3).map(x => (x(0) -> x(1).toDouble)).toMap)
  }





