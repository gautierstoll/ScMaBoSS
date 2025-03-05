//import org.apache.logging.log4j.core.config.composite.MergeStrategy
//import org.apache.logging.log4j.core.config.composite.MergeStrategy._
//import org.apache.logging.log4j.core.config.composite.MergeStrategy
import sun.security.tools.PathList._

name := "ScMaBoSS"

version := "0.8.2" // parralel version of UPMaBoSS

scalaVersion := "2.12.19"
crossScalaVersions := Seq("2.12.1","2.13.0")



libraryDependencies += "io.github.pityka" %% "nspl-awt" % "0.0.21" % "provided"
//libraryDependencies +=
  //"io.github.pityka" %% "saddle-core-fork" % "1.3.4-fork1" exclude ("com.googlecode.efficient-java-matrix-library", "ejml")
  //"io.github.pityka" %% "saddle-core-fork" % "1.3.4-fork1"
// libraryDependencies += "io.github.pityka" %% "stat" % "0.0.8"
  libraryDependencies ++= Seq(
  "org.scala-saddle" %% "saddle-core" % "1.3.5-SNAPSHOT" % "provided"
  // (OPTIONAL) "org.scala-saddle" %% "saddle-hdf5" % "1.3.+"
)

libraryDependencies += "io.github.pityka" %% "nspl-saddle" % "0.0.21" % "provided"

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases"
)
resolvers += Opts.resolver.sonatypeSnapshots
