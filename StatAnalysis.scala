package ScMaBoSS

class DataFrame(val doubleNames:List[String],
                val longNames:List[String],val qualNamesKeys:List[(String,Set[String])],
                doubleVal : List[Map[String,Double]],
                longVal : List[Map[String,Long]],
                qualVal : List[Map[String,String]]) {
  private val doubleDataNoEmptyLines : List[List[(String,Option[Double])]] = doubleVal.map(mapDbl =>
    doubleNames.map(dName => (dName , mapDbl.get(dName))))
  private val longDataNoEmptyLines : List[List[(String,Option[Long])]] = longVal.map(mapLong =>
    doubleNames.map(dName => (dName , mapLong.get(dName))))
  private val qualDataNoEmptyLines : List[List[(String,Option[String])]] = qualVal.map(mapQual => {
    qualNamesKeys.map(qualNameKeys =>  {
      mapQual.get(qualNameKeys._1) match {
        case Some(value) =>  (qualNameKeys._1 , {Some(value).filter(qualNameKeys._2.contains)})
        case _ => (qualNameKeys._1 , None)
      }
    })
  })

  private val maxSize = List(doubleDataNoEmptyLines.size,longDataNoEmptyLines.size,qualDataNoEmptyLines.size).max
  val doubleData : List[List[(String,Option[Double])]] =
    doubleDataNoEmptyLines ::: List.fill(maxSize - doubleDataNoEmptyLines.size)(doubleNames.map(x=> (x,None)))
  val longData : List[List[(String,Option[Long])]] =
    longDataNoEmptyLines ::: List.fill(maxSize - longDataNoEmptyLines.size)(longNames.map(x=> (x,None)))

  val qualData : List[List[(String,Option[String])]] =
    qualDataNoEmptyLines ::: List.fill(maxSize - qualDataNoEmptyLines.size)(qualNamesKeys.map(x=> (x._1,None:Option[String])))

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

