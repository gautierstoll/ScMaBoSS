package ScMaBoSS

object DataFrame {
  /** Non robust Data Frame constructor. Careful, row order is inverted in order to have tail recursion
    *
    * @param doubleNames double column names
    * @param longNames long column names
    * @param qualNames qualitive column names
    * @param doubleColList list of columns, names attributed automatically
    * @param longColList list of columns, names attributed automatically
    * @param qualColList list of columns, names attributed automatically, possible value are computed (qualNamesKeys)
    * @return
    */
  def FromColList(doubleNames: List[String] = List(), longNames: List[String] = List(),
                  qualNames: List[String] = List(),
                  doubleColList: List[List[Double]]=List(), longColList: List[List[Long]]=List(),
                  qualColList: List[List[String]]=List()): DataFrame = {
    def constructListMap[A](colNames: List[String], lList: List[List[A]]): (List[String], List[Map[String, A]]) = {
      def transposeLL[B](llistVal: List[List[B]],cumulList : List[List[Option[B]]] = List() ): List[List[Option[B]]] = {
        if (llistVal.map(_.isEmpty).reduce(_ & _)) {cumulList} else {
            transposeLL(llistVal.map(list => if (list.isEmpty) {List[B]()} else list.tail),
              llistVal.map(list => if (list.isEmpty) {None: Option[B]} else {Some(list.head)}) :: cumulList)}
      }
      val finalColNames = colNames.take(lList.size)
      val listMap = if (finalColNames.isEmpty) {List[Map[String, A]]()} else {
        transposeLL(lList.take(finalColNames.size)).map(line => line.zip(finalColNames).flatMap(pair => {
          pair match {
            case (Some(d), n) => Some(n, d): Option[(String, A)]
            case _ => None: Option[(String, A)]
          }}).toMap)}
      (finalColNames, listMap)
    }
    val doubleListMap = constructListMap[Double](doubleNames, doubleColList)
    val longListMap = constructListMap(longNames, longColList)
    val qualListMap = constructListMap(qualNames, qualColList)
    val qualNamesKeys: List[(String, Set[String])] =
      if (qualListMap._1.isEmpty) {List()} else {
      qualListMap._1.zip(qualColList.map(_.toSet))}
    new DataFrame(doubleListMap._1, longListMap._1, qualNamesKeys, doubleListMap._2, longListMap._2, qualListMap._2)
  }
}


/** Statically typed dataFrame, robust constructor
  *
  * @param doubleNames only these names are used for data in doubleVal maps
  * @param longNames only these names are used for data in longVal maps
  * @param qualNamesKeys only these names are used for data in qualVal maps
  * @param doubleVal Must have the keys of doubleNames
  * @param longVal Must have the keys of longNames
  * @param qualVal Must have the keys of qualNamesKeys ._1 and the values of qualNamesKeys._2
  */
class DataFrame(val doubleNames:List[String] = List(),
                val longNames:List[String] = List() ,val qualNamesKeys:List[(String,Set[String])] = List(),
                doubleVal : List[Map[String,Double]] = List(),
                longVal : List[Map[String,Long]] = List(),
                qualVal : List[Map[String,String]] = List() ) {


  private val doubleDataNoEmptyLines : List[List[(String,Option[Double])]] = doubleVal.map(mapDbl =>
    doubleNames.map(dName => (dName , mapDbl.get(dName))))
  private val longDataNoEmptyLines : List[List[(String,Option[Long])]] = longVal.map(mapLong =>
    longNames.map(dName => (dName , mapLong.get(dName))))
  private val qualDataNoEmptyLines : List[List[(String,Option[String])]] = qualVal.map(mapQual => {
    qualNamesKeys.map(qualNameKeys =>  {
      mapQual.get(qualNameKeys._1) match {
        case Some(value) =>  (qualNameKeys._1 , {Some(value).filter(qualNameKeys._2.contains)})
        case _ => (qualNameKeys._1 , None)
      }
    })
  })

  private val maxSize = List(doubleDataNoEmptyLines.size,longDataNoEmptyLines.size,qualDataNoEmptyLines.size).max
  /** Double data, Option for empty values, same length as the longest one
    *
    */
  val doubleData : List[List[(String,Option[Double])]] = {
    if(doubleDataNoEmptyLines.isEmpty) {List.fill(maxSize)(List[(String,Option[Double])]())}
    else {doubleDataNoEmptyLines ::: List.fill(maxSize - doubleDataNoEmptyLines.size)(doubleNames.map(x=> (x,None)))}
  }
  /** Long data, Option for empty values, same length as the longest one
    *
    */
  val longData : List[List[(String,Option[Long])]] =
    if (longDataNoEmptyLines.isEmpty) {List.fill(maxSize)(List[(String,Option[Long])]())}
    else {longDataNoEmptyLines ::: List.fill(maxSize - longDataNoEmptyLines.size)(longNames.map(x=> (x,None)))}

  /** Qualitative data, Option for empty values, same length as the longest one
    *
    */
  val qualData : List[List[(String,Option[String])]] =
    if (qualDataNoEmptyLines.isEmpty){List.fill(maxSize)(List[(String,Option[String])]())}
    else {qualDataNoEmptyLines ::: List.fill(maxSize - qualDataNoEmptyLines.size)(qualNamesKeys.map(x=> (x._1,None:Option[String])))}

  /** Useful for exporting the table to a file
    *
    * @return
    */
  override def toString: String =
    (if(doubleNames.nonEmpty) {doubleNames.mkString("\t") + "\t"} else {""}) +
      (if(longNames.nonEmpty) {longNames.mkString("\t") + "\t"} else {""}) +
      qualNamesKeys.map(_._1).mkString("\t") + "\n" +
    doubleData.map(dblVals => dblVals.map(dblOVal => dblOVal._2 match {
      case Some(vl) => vl.toString
      case _ => "NA"
    }).mkString("\t")).zip(
      longData.map(lgVals => lgVals.map(lgOVal => lgOVal._2 match {
        case Some(vl) => vl.toString
        case _ => "NA"
      }).mkString("\t"))
    ).zip(
      qualData.map(qVals => qVals.map(qOVal => qOVal._2.getOrElse("NA")).mkString("\t"))).
      map(x => ((if(x._1._1.nonEmpty){x._1._1 + "\t"} else{""}) +
        (if(x._1._2.nonEmpty){x._1._2 + "\t"}else{""}) + x._2)).mkString("\n")
}

