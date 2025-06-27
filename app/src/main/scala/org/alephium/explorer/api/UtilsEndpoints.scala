// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import scala.collection.immutable.ArraySeq

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.Endpoints.jsonBody
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model.LogbackValue
import org.alephium.explorer.persistence.queries.ExplainResult

// scalastyle:off magic.number
trait UtilsEndpoints extends BaseEndpoint with QueryParams {

  private val utilsEndpoint =
    baseEndpoint
      .tag("Utils")
      .in("utils")

  private val logLevels    = List("TRACE", "DEBUG", "INFO", "WARN", "ERROR")
  private val logLevelsStr = logLevels.mkString(", ")

  val sanityCheck: BaseEndpoint[Unit, Unit] =
    utilsEndpoint.put
      .in("sanity-check")
      .summary("Perform a sanity check")

  val indexCheck: BaseEndpoint[Unit, ArraySeq[ExplainResult]] =
    utilsEndpoint.get
      .in("index-check")
      .out(jsonBody[ArraySeq[ExplainResult]])
      .summary("Perform index check")

  val changeGlobalLogLevel: BaseEndpoint[String, Unit] =
    utilsEndpoint.put
      .in("update-global-loglevel")
      .in(plainBody[String].validate(Validator.enumeration(logLevels)))
      .summary(s"Update global log level, accepted: $logLevelsStr")

  val changeLogConfig: BaseEndpoint[ArraySeq[LogbackValue], Unit] =
    utilsEndpoint.put
      .in("update-log-config")
      .in(jsonBody[ArraySeq[LogbackValue]])
      .summary("Update logback values")
}
