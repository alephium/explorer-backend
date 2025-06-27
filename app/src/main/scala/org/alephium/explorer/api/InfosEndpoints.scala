// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import java.math.BigDecimal

import scala.collection.immutable.ArraySeq

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.Endpoints.jsonBody
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model._

// scalastyle:off magic.number
trait InfosEndpoints extends BaseEndpoint with QueryParams {

  private val infosEndpoint =
    baseEndpoint
      .tag("Infos")
      .in("infos")

  private val supplyEndpoint =
    infosEndpoint
      .in("supply")

  val getInfos: BaseEndpoint[Unit, ExplorerInfo] =
    infosEndpoint.get
      .out(jsonBody[ExplorerInfo])
      .summary("Get explorer informations")

  val listTokenSupply: BaseEndpoint[Pagination, ArraySeq[TokenSupply]] =
    supplyEndpoint.get
      .in(pagination)
      .out(jsonBody[ArraySeq[TokenSupply]])
      .summary("Get token supply list")

  val getCirculatingSupply: BaseEndpoint[Unit, BigDecimal] =
    supplyEndpoint.get
      .in("circulating-alph")
      .out(plainBody[BigDecimal])
      .summary("Get the ALPH circulating supply")

  val getTotalSupply: BaseEndpoint[Unit, BigDecimal] =
    supplyEndpoint.get
      .in("total-alph")
      .out(plainBody[BigDecimal])
      .summary("Get the ALPH total supply")

  val getReservedSupply: BaseEndpoint[Unit, BigDecimal] =
    supplyEndpoint.get
      .in("reserved-alph")
      .out(plainBody[BigDecimal])
      .summary("Get the ALPH reserved supply")

  val getLockedSupply: BaseEndpoint[Unit, BigDecimal] =
    supplyEndpoint.get
      .in("locked-alph")
      .out(plainBody[BigDecimal])
      .summary("Get the ALPH locked supply")

  val getHeights: BaseEndpoint[Unit, ArraySeq[PerChainHeight]] =
    infosEndpoint.get
      .in("heights")
      .out(jsonBody[ArraySeq[PerChainHeight]])
      .summary("List latest height for each chain")

  val getTotalTransactions: BaseEndpoint[Unit, Int] =
    infosEndpoint.get
      .in("total-transactions")
      .out(plainBody[Int])
      .summary("Get the total number of transactions")

  val getAverageBlockTime: BaseEndpoint[Unit, ArraySeq[PerChainDuration]] =
    infosEndpoint.get
      .in("average-block-times")
      .out(jsonBody[ArraySeq[PerChainDuration]])
      .summary("Get the average block time for each chain")
}
