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

import scala.collection.immutable.ArraySeq

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.Endpoints.jsonBody
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model.{Hashrate, IntervalType, PerChainTimedCount, TimedCount}

// scalastyle:off magic.number
trait ChartsEndpoints extends BaseEndpoint with QueryParams {

  val intervalTypes: String = IntervalType.all.dropRight(1).map(_.string).mkString(", ")

  private val chartsEndpoint =
    baseEndpoint
      .tag("Charts")
      .in("charts")

  val getHashrates: BaseEndpoint[(TimeInterval, IntervalType), ArraySeq[Hashrate]] =
    chartsEndpoint.get
      .in("hashrates")
      .in(timeIntervalQuery)
      .in(intervalTypeQuery)
      .out(jsonBody[ArraySeq[Hashrate]])
      .description(s"`interval-type` query param: $intervalTypes")
      .summary("Get hashrate chart in H/s")

  val getAllChainsTxCount: BaseEndpoint[(TimeInterval, IntervalType), ArraySeq[TimedCount]] =
    chartsEndpoint.get
      .in("transactions-count")
      .in(timeIntervalQuery)
      .in(intervalTypeQuery)
      .out(jsonBody[ArraySeq[TimedCount]])
      .description(s"`interval-type` query param: ${intervalTypes}")
      .summary("Get transaction count history")

  val getPerChainTxCount: BaseEndpoint[(TimeInterval, IntervalType), ArraySeq[PerChainTimedCount]] =
    chartsEndpoint.get
      .in("transactions-count-per-chain")
      .in(timeIntervalQuery)
      .in(intervalTypeQuery)
      .out(jsonBody[ArraySeq[PerChainTimedCount]])
      .description(s"`interval-type` query param: ${intervalTypes}")
      .summary("Get transaction count history per chain")
}
