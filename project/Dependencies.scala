// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

import sbt._

object Version {
  lazy val common = "4.5.5"

  lazy val pekko      = "1.6.0"
  lazy val rxJava     = "3.1.12"
  lazy val tapir      = "1.13.21"
  lazy val vertx      = "5.1.2"
  lazy val slick      = "3.6.1"
  lazy val postgresql = "42.7.11"
  lazy val sttp       = "4.0.23"
  lazy val apispec    = "0.11.10"
  lazy val prometheus = "1.6.1"
  lazy val micrometer = "1.13.0"
}

object Dependencies {
  lazy val alephiumCrypto   = "org.alephium" %% "alephium-crypto"   % Version.common
  lazy val alephiumProtocol = "org.alephium" %% "alephium-protocol" % Version.common
  lazy val alephiumUtil     = "org.alephium" %% "alephium-util"     % Version.common
  lazy val alephiumApi      = "org.alephium" %% "alephium-api"      % Version.common
  lazy val alephiumJson     = "org.alephium" %% "alephium-json"     % Version.common
  lazy val alephiumHttp     = "org.alephium" %% "alephium-http"     % Version.common
  lazy val alephiumConf     = "org.alephium" %% "alephium-conf"     % Version.common

  lazy val vertx       = "io.vertx" % "vertx-core"     % Version.vertx
  lazy val vertxRxJava = "io.vertx" % "vertx-rx-java3" % Version.vertx

  lazy val pekkoTest = "org.apache.pekko" %% "pekko-testkit" % Version.pekko % Test

  lazy val rxJava = "io.reactivex.rxjava3" % "rxjava" % Version.rxJava

  lazy val tapirCore   = "com.softwaremill.sttp.tapir" %% "tapir-core"         % Version.tapir
  lazy val tapirServer = "com.softwaremill.sttp.tapir" %% "tapir-server"       % Version.tapir
  lazy val tapirVertx  = "com.softwaremill.sttp.tapir" %% "tapir-vertx-server" % Version.tapir

  lazy val tapirOpenapi   = "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % Version.tapir
  lazy val tapirSwaggerUi = "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui"   % Version.tapir
  lazy val tapirOpenapiModel = "com.softwaremill.sttp.apispec" %% "openapi-model" % Version.apispec
  lazy val tapirClient = "com.softwaremill.sttp.tapir"   %% "tapir-sttp-client" % Version.tapir
  lazy val sttpBackend = "com.softwaremill.sttp.client4" %% "core"              % Version.sttp

  lazy val `upickle` = "com.lihaoyi" %% "upickle" % "4.4.3"

  lazy val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % "3.1.8"

  lazy val ficus = "com.iheart" %% "ficus" % "1.5.2"

  lazy val scalatest     = "org.scalatest"              %% "scalatest"       % "3.2.18"   % Test
  lazy val scalacheck    = "org.scalacheck"             %% "scalacheck"      % "1.17.1"   % Test
  lazy val scalatestplus = "org.scalatestplus"          %% "scalacheck-1-17" % "3.2.18.0" % Test
  lazy val scalaMock     = "org.scalamock"              %% "scalamock"       % "6.0.0"    % Test
  lazy val scalaLogging  = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5"
  lazy val logback       = "ch.qos.logback"              % "logback-classic" % "1.5.32"

  lazy val slick      = "com.typesafe.slick" %% "slick"      % Version.slick
  lazy val postgresql = "org.postgresql"      % "postgresql" % Version.postgresql

  lazy val slickHikaricp = "com.typesafe.slick" %% "slick-hikaricp" % Version.slick

  lazy val prometheusSimpleClient = "io.prometheus" % "prometheus-metrics-core" % Version.prometheus
  lazy val prometheusSimpleClientCommon =
    "io.prometheus" % "prometheus-metrics-exporter-common" % Version.prometheus
  lazy val prometheusSimpleClientHotspot =
    "io.prometheus" % "prometheus-metrics-instrumentation-jvm" % Version.prometheus
  lazy val tapirPrometheusMetrics =
    "com.softwaremill.sttp.tapir" %% "tapir-prometheus-metrics" % Version.tapir
  lazy val micrometerCore = "io.micrometer" % "micrometer-core" % Version.micrometer
  lazy val micrometerPrometheus =
    "io.micrometer" % "micrometer-registry-prometheus" % Version.micrometer
}
