// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import scala.collection.immutable.ArraySeq

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.Endpoints.jsonBody
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model._

// scalastyle:off magic.number
trait MarketEndpoints extends BaseEndpoint with QueryParams {

  private val basePricesEndpoint =
    baseEndpoint
      .in("market")
      .tag("Market")

  implicit val valueSchema: Schema[ujson.Value] = Schema.string

  val getPrices: BaseEndpoint[(String, ArraySeq[String]), ArraySeq[Option[Double]]] =
    basePricesEndpoint.post
      .in("prices")
      .in(query[String]("currency"))
      .in(jsonBody[ArraySeq[String]])
      .out(jsonBody[ArraySeq[Option[Double]]])

  val getPriceChart: BaseEndpoint[(String, String), TimedPrices] =
    basePricesEndpoint.get
      .in("prices")
      .in(path[String]("symbol"))
      .in(query[String]("currency"))
      .in("charts")
      .out(jsonBody[TimedPrices])
}
