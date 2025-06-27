// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import org.scalacheck.Gen

import org.alephium.explorer.{AlephiumActorSpecLike, HttpServerFixture}
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

  val utxServer = new MempoolServer()

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
