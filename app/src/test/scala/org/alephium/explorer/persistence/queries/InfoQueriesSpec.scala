// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.model.{HolderEntity, TokenHolderEntity}
import org.alephium.explorer.persistence.schema.{AlphHolderSchema, TokenHolderSchema}
import org.alephium.util.U256

class InfoQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with TestDBRunner {

  "getAlphHoldersAction" should {
    "return holders ordered by balance descending" in {
      val addresses = Gen.listOfN(3, addressGen).sample.get
      val balances  = List(U256.unsafe(10), U256.unsafe(30), U256.unsafe(20))
      val holders = addresses.zip(balances).map { case (address, balance) =>
        HolderEntity(address, balance)
      }

      exec(AlphHolderSchema.table.delete)
      exec(AlphHolderSchema.table ++= holders)

      val result = exec(InfoQueries.getAlphHoldersAction(Pagination.unsafe(1, 2)))

      result is holders
        .sortBy(_.balance)(Ordering[U256].reverse)
        .take(2)
        .map(h => (h.address, h.balance))
    }
  }

  "getTokenHoldersAction" should {
    "filter by token and return holders ordered by balance descending" in {
      val token         = tokenIdGen.sample.get
      val otherToken    = tokenIdGen.sample.get
      val firstAddress  = addressGen.sample.get
      val secondAddress = addressGen.sample.get
      val thirdAddress  = addressGen.sample.get

      val holders = Seq(
        TokenHolderEntity(firstAddress, token, U256.unsafe(5)),
        TokenHolderEntity(secondAddress, token, U256.unsafe(15)),
        TokenHolderEntity(thirdAddress, otherToken, U256.unsafe(100))
      )

      exec(TokenHolderSchema.table.delete)
      exec(TokenHolderSchema.table ++= holders)

      val result = exec(InfoQueries.getTokenHoldersAction(token, Pagination.unsafe(1, 10)))

      result is Seq(
        (secondAddress, U256.unsafe(15)),
        (firstAddress, U256.unsafe(5))
      )
    }
  }
}
