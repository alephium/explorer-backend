import sbt._

object Version {
  lazy val common = "0.2.0-SNAPSHOT"

  lazy val akka  = "2.6.5"
  lazy val circe = "0.13.0"
  lazy val tapir = "0.14.5"
}
object Dependencies {
  lazy val alephiumUtil = "org.alephium" %% "util" % Version.common
  lazy val alephiumRpc  = "org.alephium" %% "rpc"  % Version.common

  lazy val akkaTest       = "com.typesafe.akka" %% "akka-testkit"        % Version.akka % Test
  lazy val akkaHttptest   = "com.typesafe.akka" %% "akka-http-testkit"   % "10.1.11"    % Test
  lazy val akkaStreamTest = "com.typesafe.akka" %% "akka-stream-testkit" % Version.akka % Test

  lazy val tapirCore        = "com.softwaremill.sttp.tapir" %% "tapir-core"               % Version.tapir
  lazy val tapirCirce       = "com.softwaremill.sttp.tapir" %% "tapir-json-circe"         % Version.tapir
  lazy val tapirAkka        = "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server"   % Version.tapir
  lazy val tapirOpenapi     = "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"       % Version.tapir
  lazy val tapiOpenapiCirce = "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % Version.tapir

  lazy val circeCore    = "io.circe" %% "circe-core"    % Version.circe
  lazy val circeGeneric = "io.circe" %% "circe-generic" % Version.circe

  lazy val scalatest     = "org.scalatest"              %% "scalatest"       % "3.1.1" % Test
  lazy val scalacheck    = "org.scalacheck"             %% "scalacheck"      % "1.14.3" % Test
  lazy val scalatestplus = "org.scalatestplus"          %% "scalacheck-1-14" % "3.1.1.1" % Test
  lazy val scalaLogging  = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.2"
  lazy val logback       = "ch.qos.logback"             % "logback-classic"  % "1.2.3"
}
