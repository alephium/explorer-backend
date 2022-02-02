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
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.api.BaseEndpoint
import org.alephium.explorer.api.model.Hashrate

// scalastyle:off magic.number
trait ChartsEndpoints extends BaseEndpoint with QueryParams {

  private val chartsEndpoint =
    baseEndpoint
      .tag("Charts")
      .in("charts")

  val getHashrates: BaseEndpoint[(TimeInterval, Int), Seq[Hashrate]] =
    chartsEndpoint.get
      .in("hashrates")
      .in(timeIntervalQuery)
      .in(query[Int]("interval").validate(Validator.min(0)).validate(Validator.max(2)))
      .out(jsonBody[Seq[Hashrate]])
      .summary("Get explorer informations.")
      .description("`interval` query param: 0 = 10 minutes, 1 = hourly, 2 = daily")
}
