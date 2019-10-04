name := "ScMaBoSS"

version := "0.1"

scalaVersion := "2.13.0"

libraryDependencies += "io.github.pityka" %% "nspl-awt" % "0.0.19"

libraryDependencies +=
  "io.github.pityka" %% "saddle-core-fork" % "1.3.4-fork1" exclude ("com.googlecode.efficient-java-matrix-library", "ejml")


libraryDependencies += "io.github.pityka" %% "stat" % "0.0.8"

libraryDependencies += "io.github.pityka" %% "nspl-saddle" % "0.0.19"


resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
resolvers += Opts.resolver.sonatypeSnapshots