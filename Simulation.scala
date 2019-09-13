import java.io.FileNotFoundException
import java.nio.file.{Paths, Files}
import scala.io.Source

class Simulation(bndFile: String, cfgFile: String = null, cfgFiles: List[String] = null) {
  val config : String = {
    if (cfgFile != null) {file_get_content(cfgFile)}
    else if (cfgFiles !=null) {cfgFiles.map(x => file_get_content(x)).mkString}
    else throw new Exception("Simulation: cfgfile or cfgfiles must be set")
  }

  def file_get_content(filename: String): String = {
    if (!Files.exists(Paths.get(filename))) throw new FileNotFoundException(filename + "is not valid")
    val bufferedSource = try {
      Source.fromFile(filename)
    } catch {
      case _: Throwable => throw new FileNotFoundException(filename + "is not readable")
    }
    val content = bufferedSource.getLines.mkString
    bufferedSource.close()
    content
  }
}
