// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

import sbt._

object Version {
  lazy val common = "0.6.2"

  lazy val akka       = "2.6.8"
  lazy val circe      = "0.13.0"
  lazy val tapir      = "0.16.16"
  lazy val slick      = "3.3.2"
  lazy val postgresql = "42.2.12"
  lazy val sttp       = "2.2.5"
}
object Dependencies {
  lazy val alephiumCrypto   = "org.alephium" %% "alephium-crypto"   % Version.common
  lazy val alephiumProtocol = "org.alephium" %% "alephium-protocol" % Version.common
  lazy val alephiumUtil     = "org.alephium" %% "alephium-util"     % Version.common
  lazy val alephiumApi      = "org.alephium" %% "alephium-api"      % Version.common

  lazy val akkaTest       = "com.typesafe.akka" %% "akka-testkit"        % Version.akka % Test
  lazy val akkaHttptest   = "com.typesafe.akka" %% "akka-http-testkit"   % "10.1.12" % Test
  lazy val akkaStream     = "com.typesafe.akka" %% "akka-stream-typed"   % Version.akka
  lazy val akkaStreamTest = "com.typesafe.akka" %% "akka-stream-testkit" % Version.akka % Test

  lazy val akkaHttpCors = "ch.megard" %% "akka-http-cors" % "0.4.3"

  lazy val tapirCore        = "com.softwaremill.sttp.tapir"  %% "tapir-core"                 % Version.tapir
  lazy val tapirCirce       = "com.softwaremill.sttp.tapir"  %% "tapir-json-circe"           % Version.tapir
  lazy val tapirAkka        = "com.softwaremill.sttp.tapir"  %% "tapir-akka-http-server"     % Version.tapir
  lazy val tapirOpenapi     = "com.softwaremill.sttp.tapir"  %% "tapir-openapi-docs"         % Version.tapir
  lazy val tapiOpenapiCirce = "com.softwaremill.sttp.tapir"  %% "tapir-openapi-circe-yaml"   % Version.tapir
  lazy val tapirSwaggerUi   = "com.softwaremill.sttp.tapir"  %% "tapir-swagger-ui-akka-http" % Version.tapir
  lazy val tapirClient      = "com.softwaremill.sttp.tapir"  %% "tapir-sttp-client"          % Version.tapir
  lazy val sttpAkkaBackend  = "com.softwaremill.sttp.client" %% "akka-http-backend"          % Version.sttp

  lazy val circeCore    = "io.circe" %% "circe-core"    % Version.circe
  lazy val circeGeneric = "io.circe" %% "circe-generic" % Version.circe

  lazy val akkaHttpCirce = "de.heikoseeberger" %% "akka-http-circe" % "1.32.0"

  lazy val scalatest     = "org.scalatest"              %% "scalatest"       % "3.1.1" % Test
  lazy val scalacheck    = "org.scalacheck"             %% "scalacheck"      % "1.14.3" % Test
  lazy val scalatestplus = "org.scalatestplus"          %% "scalacheck-1-14" % "3.1.1.1" % Test
  lazy val scalaLogging  = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.2"
  lazy val logback       = "ch.qos.logback"             % "logback-classic"  % "1.2.3"

  lazy val slick      = "com.typesafe.slick" %% "slick"     % Version.slick
  lazy val postgresql = "org.postgresql"     % "postgresql" % Version.postgresql

  lazy val h2            = "com.h2database"     % "h2"              % "1.4.200"
  lazy val slickHikaricp = "com.typesafe.slick" %% "slick-hikaricp" % Version.slick
}
