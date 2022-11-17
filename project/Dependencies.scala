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
  lazy val common = "1.5.0"

  lazy val akka       = "2.6.20"
  lazy val tapir      = "1.0.6"
  lazy val slick      = "3.3.2"
  lazy val postgresql = "42.2.12"
  lazy val sttp       = "3.7.4"
  lazy val apispec    = "0.2.1"
  lazy val prometheus = "0.15.0"
  lazy val micrometer = "1.7.4"
}

object Dependencies {
  lazy val alephiumCrypto   = "org.alephium" %% "alephium-crypto"   % Version.common
  lazy val alephiumProtocol = "org.alephium" %% "alephium-protocol" % Version.common
  lazy val alephiumUtil     = "org.alephium" %% "alephium-util"     % Version.common
  lazy val alephiumApi      = "org.alephium" %% "alephium-api"      % Version.common
  lazy val alephiumJson     = "org.alephium" %% "alephium-json"     % Version.common
  lazy val alephiumHttp     = "org.alephium" %% "alephium-http"     % Version.common
  lazy val alephiumConf     = "org.alephium" %% "alephium-conf"     % Version.common

  lazy val vertx                = "io.vertx" % "vertx-core"             % "4.3.3"
  lazy val vertxReactiveStreams = "io.vertx" % "vertx-reactive-streams" % "4.3.3"

  lazy val akkaStreams = "com.typesafe.akka" %% "akka-stream"  % Version.akka
  lazy val akkaTest    = "com.typesafe.akka" %% "akka-testkit" % Version.akka % Test

  lazy val tapirCore   = "com.softwaremill.sttp.tapir" %% "tapir-core"         % Version.tapir
  lazy val tapirServer = "com.softwaremill.sttp.tapir" %% "tapir-server"       % Version.tapir
  lazy val tapirVertx  = "com.softwaremill.sttp.tapir" %% "tapir-vertx-server" % "1.2.1+30-785025b7-SNAPSHOT"

  lazy val tapirOpenapi      = "com.softwaremill.sttp.tapir"   %% "tapir-openapi-docs"               % Version.tapir
  lazy val tapirSwaggerUi    = "com.softwaremill.sttp.tapir"   %% "tapir-swagger-ui"                 % Version.tapir
  lazy val tapirOpenapiModel = "com.softwaremill.sttp.apispec" %% "openapi-model"                    % Version.apispec
  lazy val tapirClient       = "com.softwaremill.sttp.tapir"   %% "tapir-sttp-client"                % Version.tapir
  lazy val sttpBackend       = "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % Version.sttp

  lazy val `upickle` = "com.lihaoyi" %% "upickle" % "1.6.0"

  lazy val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % "3.0.5"

  lazy val ficus = "com.iheart" %% "ficus" % "1.5.2"

  lazy val scalatest     = "org.scalatest"              %% "scalatest"       % "3.2.13" % Test
  lazy val scalacheck    = "org.scalacheck"             %% "scalacheck"      % "1.14.3" % Test
  lazy val scalatestplus = "org.scalatestplus"          %% "scalacheck-1-16" % "3.2.13.0" % Test
  lazy val scalaMock     = "org.scalamock"              %% "scalamock"       % "5.2.0" % Test
  lazy val scalaLogging  = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.2"
  lazy val logback       = "ch.qos.logback"             % "logback-classic"  % "1.2.3"

  lazy val slick      = "com.typesafe.slick" %% "slick"     % Version.slick
  lazy val postgresql = "org.postgresql"     % "postgresql" % Version.postgresql

  lazy val slickHikaricp = "com.typesafe.slick" %% "slick-hikaricp" % Version.slick

  lazy val prometheusSimpleClient        = "io.prometheus"               % "simpleclient"                   % Version.prometheus
  lazy val prometheusSimpleClientHotspot = "io.prometheus"               % "simpleclient_hotspot"           % Version.prometheus
  lazy val tapirPrometheusMetrics        = "com.softwaremill.sttp.tapir" %% "tapir-prometheus-metrics"      % Version.tapir
  lazy val micrometerCore                = "io.micrometer"               % "micrometer-core"                % Version.micrometer
  lazy val micrometerPrometheus          = "io.micrometer"               % "micrometer-registry-prometheus" % Version.micrometer
}
