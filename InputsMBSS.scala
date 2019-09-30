import java.io.FileNotFoundException
import java.nio.file.{Paths, Files}
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

object BndMbssFromFile {
  def apply(filename : String): BndMbss = {new BndMbss(ManageInputFile.file_get_content(filename))}
}

class BndMbss(val bnd : String) {
}

object CfgMbssFromFile {
  def apply(filename : String,bndMbss : BndMbss) : CfgMbss = {
    new CfgMbss(ManageInputFile.file_get_content(filename),bndMbss)}
}

class CfgMbss(val cfg : String,val bndMbss : BndMbss) {}

class Simulation(bndFile : String, cfgFile : String = null, cfgFiles: List[String] = null) {
  val boolNetDescr : BndMbss = BndMbssFromFile(bndFile)
  val network = boolNetDescr.bnd
  //val network = ManageInputFile.file_get_content(bndFile)
  val config : String = {
    if (cfgFile != null) {ManageInputFile.file_get_content(cfgFile)}
    else if (cfgFiles !=null) {cfgFiles.map(x => ManageInputFile.file_get_content(x)).mkString}
    else throw new Exception("Simulation: cfgfile or cfgfiles must be set")
  }
}
