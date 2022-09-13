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

package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.{alphJsonBody => jsonBody}
import org.alephium.api.UtilJson._
import org.alephium.explorer.api.model.LogbackValue
import org.alephium.explorer.persistence.queries.ExplainResult
import org.alephium.util.AVector

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
      .description("Perform a sanity check")

  val indexCheck: BaseEndpoint[Unit, AVector[ExplainResult]] =
    utilsEndpoint.get
      .in("index-check")
      .out(jsonBody[AVector[ExplainResult]])
      .description("Perform index check")

  val changeGlobalLogLevel: BaseEndpoint[String, Unit] =
    utilsEndpoint.put
      .in("update-global-loglevel")
      .in(plainBody[String].validate(Validator.enumeration(logLevels)))
      .description(s"Update global log level, accepted: $logLevelsStr")

  val changeLogConfig: BaseEndpoint[AVector[LogbackValue], Unit] =
    utilsEndpoint.put
      .in("update-log-config")
      .in(jsonBody[AVector[LogbackValue]])
      .description("Update logback values")
}
