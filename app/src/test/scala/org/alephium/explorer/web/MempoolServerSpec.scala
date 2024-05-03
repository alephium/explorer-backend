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

package org.alephium.explorer.web

import org.scalacheck.Gen

import org.alephium.explorer.{AlephiumActorSpecLike, ConfigDefaults, HttpServerFixture}
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixtureForAll
import org.alephium.explorer.persistence.dao.MempoolDao

@SuppressWarnings(Array("org.wartremover.warts.ThreadSleep", "org.wartremover.warts.Var"))
class MempoolServerSpec()
    extends AlephiumActorSpecLike
    with HttpServerFixture
    with DatabaseFixtureForAll {

  val utxServer = new MempoolServer(ConfigDefaults.servicesConfig.mempoolSync)

  val routes = utxServer.routes

  "listMempoolTransactions" in {
    Get(s"/mempool/transactions") check { response =>
      response.as[Seq[MempoolTransaction]] is Seq.empty
    }

    forAll(Gen.listOf(mempooltransactionGen), Gen.choose(1, 2), Gen.choose(2, 4)) {
      case (utxs, page, limit) =>
        MempoolDao.insertMany(utxs).futureValue
        Get(s"/mempool/transactions?page=$page&limit=$limit") check { response =>
          val offset = page - 1
          val drop   = offset * limit
          response.as[Seq[MempoolTransaction]] is utxs
            .sortBy(_.lastSeen)
            .reverse
            .slice(drop, drop + limit)
        }
        MempoolDao.removeMany(utxs.map(_.hash)).futureValue
    }
  }
}
