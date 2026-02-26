// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import scala.collection.immutable.ArraySeq

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.Endpoints.jsonBody
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model.{
  ActiveAddressesCount,
  ExportType,
  Hashrate,
  IntervalType,
  PerChainTimedCount,
  TimedCount
}

trait ChartsEndpoints extends BaseEndpoint with QueryParams {

  private val hourlyDaily: Set[IntervalType]  = Set(IntervalType.Hourly, IntervalType.Daily)
  private val dailyMonthly: Set[IntervalType] = Set(IntervalType.Daily, IntervalType.Monthly)

  private val chartsEndpoint =
    baseEndpoint
      .tag("Charts")
      .in("charts")

  val getHashrates: BaseEndpoint[(TimeInterval, IntervalType), ArraySeq[Hashrate]] =
    chartsEndpoint.get
      .in("hashrates")
      .in(timeIntervalQuery)
      .in(intervalTypeSubsetQuery(hourlyDaily))
      .out(jsonBody[ArraySeq[Hashrate]])
      .summary("Get hashrate chart in H/s")

  val getAllChainsTxCount: BaseEndpoint[(TimeInterval, IntervalType), ArraySeq[TimedCount]] =
    chartsEndpoint.get
      .in("transactions-count")
      .in(timeIntervalQuery)
      .in(intervalTypeSubsetQuery(hourlyDaily))
      .out(jsonBody[ArraySeq[TimedCount]])
      .summary("Get transaction count history")

  val getPerChainTxCount: BaseEndpoint[(TimeInterval, IntervalType), ArraySeq[PerChainTimedCount]] =
    chartsEndpoint.get
      .in("transactions-count-per-chain")
      .in(timeIntervalQuery)
      .in(intervalTypeSubsetQuery(hourlyDaily))
      .out(jsonBody[ArraySeq[PerChainTimedCount]])
      .summary("Get transaction count history per chain")

  val getActiveAddresses
      : BaseEndpoint[(TimeInterval, IntervalType, Option[ExportType]), ActiveAddressesCount] =
    chartsEndpoint.get
      .in("active-addresses")
      .in(timeIntervalQuery)
      .in(intervalTypeSubsetQuery(dailyMonthly))
      .in(optionalExportTypeQuery)
      .out(
        oneOf[ActiveAddressesCount](
          oneOfVariant[ActiveAddressesCount.Csv](csvBody[ActiveAddressesCount.Csv]),
          oneOfVariant(jsonBody[Seq[TimedCount]].map(ActiveAddressesCount.Json(_))(_.data))
        )
      )
      .summary("Get active addresses history")

}
