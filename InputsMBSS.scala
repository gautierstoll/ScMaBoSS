import java.io.FileNotFoundException
import java.nio.file.{Files, Paths}
import scala.util.matching.Regex

import jdk.nashorn.internal.runtime.regexp.RegExp

import scala.io.Source

object ManageInputFile {
  def file_get_content(filename: String): String = {
    if (!Files.exists(Paths.get(filename))) throw new FileNotFoundException(filename + "is not valid")
    val bufferedSource = try {
      Source.fromFile(filename)
    } catch {
      case _: Throwable => throw new FileNotFoundException(filename + "is not readable")
    }
    val content = bufferedSource.getLines.mkString("\n")
    bufferedSource.close()
    content
  }
}

object BndMbss{
  def fromFile(filename : String): BndMbss = {new BndMbss(ManageInputFile.file_get_content(filename))}
}

class BndMbss(val bnd : String) {
  val nodeFields : List[String] = bnd.split("[n|N][o|O][d|D][e|E]\\s+").toList.tail.
    map("//.*".r.replaceAllIn(_,"")).map("/\\*[\\s\\S]*\\*/".r.replaceAllIn(_,""))
  val nodeList : List[String] = nodeFields.iterator.map(x => {"[^\\s]+".r.findFirstIn(x) match {
    case Some(node) => node ; case None => null}}).toList
  def mutateBnd(mutNodes : List[String]) : BndMbss = {
    val mutNodeFields: String = "node "+ nodeFields.map(field => {
      val node = "[^\\s]+".r.findFirstIn(field) match {case Some(node) => node ; case None => null}
      if (mutNodes.contains(node)) {
      val rup_field =  if ("[\\s\\S]*rate_up[\\s\\S]*".r.matches(field)) {
            "rate_up\\s*=([^;]+);".r.
              replaceAllIn(field,"rate_up = ( \\$Low_"+node+" ? 0.0 : ( \\$High_"+node+" ? @max_rate : ($1 ) ) );")
          } else {
            "\\}".r.replaceAllIn(field,"  rate_up = ( \\$Low_"+node+
              " ? 0.0 : ( \\$High_"+node+" ? @max_rate : (@logic ? 1.0 : 0.0 ) ) );\n}")}
      if ("[\\s\\S]*rate_down[\\s\\S]*".r.matches(field)) {"rate_down\\s*=([^;]+);".r.
            replaceAllIn(rup_field,"rate_down = ( \\$Low_"+node+
              " ? @max_rate : ( \\$High_"+node+" ? 0.0 : ($1 ) ) );\n" +
              "  max_rate = "+mutNodes.length.toString+";")
          } else {"\\}".r.replaceAllIn(rup_field,"  rate_down = ( \\$Low_"+node+
            " ? @max_rate : ( \\$High_"+node+" ? 0.0 : (@logic ? 0.0 : 1.0 ) ) );\n"+
            "  max_rate = "+mutNodes.length.toString+";\n}")}
      } else field }).mkString("node ")
    new BndMbss(mutNodeFields)
  }
}

object CfgMbss {
  def fromFile(filename : String,bndMbss : BndMbss) : CfgMbss = {
    new CfgMbss(bndMbss,ManageInputFile.file_get_content(filename))}
  def fromFiles(bndMbss : BndMbss,filenames : List[String]) : CfgMbss = {
  new CfgMbss(bndMbss,filenames.map(x => ManageInputFile.file_get_content(x)).mkString("\n"))
  }
}

class CfgMbss(val bndMbss : BndMbss,val cfg : String) {
  def mutatedCfg(mutNodes: List[String]): CfgMbss = {
    new CfgMbss(bndMbss.mutateBnd(mutNodes),cfg + "\n" + mutNodes.map(node => {
      "$High_" + node + " = 0;\n" + "$Low_" + node + " = 0;"}).mkString("\n"))
  }
}
