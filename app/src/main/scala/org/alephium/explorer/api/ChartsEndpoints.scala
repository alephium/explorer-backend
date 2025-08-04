// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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

  def intervalTypes: String = IntervalType.all.dropRight(1).map(_.string).mkString(", ")

  private def chartsEndpoint =
    baseEndpoint
      .tag("Charts")
      .in("charts")

  def getHashrates: BaseEndpoint[(TimeInterval, IntervalType), ArraySeq[Hashrate]] =
    chartsEndpoint.get
      .in("hashrates")
      .in(timeIntervalQuery)
      .in(intervalTypeQuery)
      .out(jsonBody[ArraySeq[Hashrate]])
      .summary("Get hashrate chart in H/s")
      .description(s"`interval-type` query param: $intervalTypes")

  def getAllChainsTxCount: BaseEndpoint[(TimeInterval, IntervalType), ArraySeq[TimedCount]] =
    chartsEndpoint.get
      .in("transactions-count")
      .in(timeIntervalQuery)
      .in(intervalTypeQuery)
      .out(jsonBody[ArraySeq[TimedCount]])
      .summary("Get transaction count history")
      .description(s"`interval-type` query param: ${intervalTypes}")

  def getPerChainTxCount: BaseEndpoint[(TimeInterval, IntervalType), ArraySeq[PerChainTimedCount]] =
    chartsEndpoint.get
      .in("transactions-count-per-chain")
      .in(timeIntervalQuery)
      .in(intervalTypeQuery)
      .out(jsonBody[ArraySeq[PerChainTimedCount]])
      .summary("Get transaction count history per chain")
      .description(s"`interval-type` query param: ${intervalTypes}")
}
