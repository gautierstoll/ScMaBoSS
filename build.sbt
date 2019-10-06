name := "ScMaBoSS"

version := "0.1"

scalaVersion := "2.12.1"
crossScalaVersions := Seq("2.12.1","2.13.0")



libraryDependencies += "io.github.pityka" %% "nspl-awt" % "0.0.21"
//libraryDependencies +=
  //"io.github.pityka" %% "saddle-core-fork" % "1.3.4-fork1" exclude ("com.googlecode.efficient-java-matrix-library", "ejml")
  //"io.github.pityka" %% "saddle-core-fork" % "1.3.4-fork1"
// libraryDependencies += "io.github.pityka" %% "stat" % "0.0.8"
  libraryDependencies ++= Seq(
  "org.scala-saddle" %% "saddle-core" % "1.3.5-SNAPSHOT"
  // (OPTIONAL) "org.scala-saddle" %% "saddle-hdf5" % "1.3.+"
)

libraryDependencies += "io.github.pityka" %% "nspl-saddle" % "0.0.21"

resolvers ++= Seq(
  "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases"
)
resolvers += Opts.resolver.sonatypeSnapshots
