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

import java.math.BigDecimal

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.{alphJsonBody => jsonBody}
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.api.BaseEndpoint
import org.alephium.explorer.api.model.{ExplorerInfo, HashRate, Pagination, TokenSupply}

// scalastyle:off magic.number
trait InfosEndpoints extends BaseEndpoint with QueryParams {

  private val infosEndpoint =
    baseEndpoint
      .tag("Infos")
      .in("infos")

  private val supplyEndpoint =
    infosEndpoint
      .in("supply")

  private val hashRateEndpoint =
    infosEndpoint
      .in("hashrate")

  val getInfos: BaseEndpoint[Unit, ExplorerInfo] =
    infosEndpoint.get
      .out(jsonBody[ExplorerInfo])
      .description("Get explorer informations")

  val listTokenSupply: BaseEndpoint[Pagination, Seq[TokenSupply]] =
    supplyEndpoint.get
      .in(pagination)
      .out(jsonBody[Seq[TokenSupply]])
      .description("Get token supply list")

  val getCirculatingSupply: BaseEndpoint[Unit, BigDecimal] =
    supplyEndpoint.get
      .in("circulating-alph")
      .out(plainBody[BigDecimal])
      .description("Get the ALPH circulating supply")

  val getTotalSupply: BaseEndpoint[Unit, BigDecimal] =
    supplyEndpoint.get
      .in("total-alph")
      .out(plainBody[BigDecimal])
      .description("Get the ALPH total supply")

  val getHashRate: BaseEndpoint[(TimeInterval, Long), Seq[HashRate]] =
    hashRateEndpoint.get
      .in(timeIntervalQuery)
      .in(query[Long]("interval").description("Time interval in millisecond"))
      .out(jsonBody[Seq[HashRate]])
      .description("Get the ALPH total supply")
}
