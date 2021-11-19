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
import org.alephium.explorer.api.BaseEndpoint
import org.alephium.explorer.api.model.{ExplorerInfo, Pagination, TokenSupply}

// scalastyle:off magic.number
trait InfosEndpoints extends BaseEndpoint with QueryParams {

  private val infosEndpoint =
    baseEndpoint
      .tag("Infos")
      .in("infos")

  val getInfos: BaseEndpoint[Unit, ExplorerInfo] =
    infosEndpoint.get
      .out(jsonBody[ExplorerInfo])
      .description("Get explorer informations")

  val listTokenSupply: BaseEndpoint[Pagination, Seq[TokenSupply]] =
    infosEndpoint.get
      .in("token-supply")
      .in(pagination)
      .out(jsonBody[Seq[TokenSupply]])
      .description("Get token supply list")

  val getTokenSupply: BaseEndpoint[Unit, TokenSupply] =
    infosEndpoint.get
      .in("latest-token-supply")
      .out(jsonBody[TokenSupply])
      .description("Get the latest token supply value")
}
