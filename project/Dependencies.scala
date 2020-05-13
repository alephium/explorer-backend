import sbt._

object Dependencies {
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.2"
  lazy val logback      = "ch.qos.logback"             % "logback-classic" % "1.2.3"
}
