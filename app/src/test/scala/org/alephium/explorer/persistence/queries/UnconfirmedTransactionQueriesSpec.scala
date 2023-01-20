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

package org.alephium.explorer.persistence.queries

import scala.util.Random

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.Generators._
import org.alephium.explorer.persistence.{DatabaseFixtureForAll, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.util.TimeStamp

class UnconfirmedTransactionQueriesSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForAll
    with DBRunner {

  /**
    * Setup tests data for table [[UnconfirmedTxSchema]]
    *
    * @param unconfirmedTxs transaction to create a table for
    */
  def createTestData(unconfirmedTxs: Iterable[UnconfirmedTxEntity]): Unit = {
    run(UnconfirmedTxSchema.table.delete).futureValue
    val persistCount = run(UnconfirmedTxSchema.table ++= unconfirmedTxs).futureValue
    persistCount should contain(unconfirmedTxs.size)
    ()
  }

  "listHashesQuery" should {
    "list unconfirmed transaction hashes" in {
      forAll(Gen.listOf(unconfirmedTransactionEntityGen())) { unconfirmedTxs =>
        //setup test data
        createTestData(unconfirmedTxs)

        //fetch expected and actual data
        val expectedHashes = unconfirmedTxs.map(_.hash)
        val actualHashes   = run(UnconfirmedTransactionQueries.listHashesQuery).futureValue

        actualHashes should contain theSameElementsAs expectedHashes
      }
    }
  }

  "listPaginatedUnconfirmedTransactionsQuery" should {
    "list paginated unconfirmed transactions" in {
      forAll(paginationDataGen(Gen.listOf(unconfirmedTransactionEntityGen()))) {
        case (unconfirmedTxs, pagination) =>
          createTestData(unconfirmedTxs)

          val expected =
            unconfirmedTxs
              .sortBy(_.lastSeen)(TimeStamp.ordering.reverse) //descending order order last_seen
              .slice(pagination.offset, pagination.offset + pagination.limit) //pagination

          val actual =
            run(UnconfirmedTransactionQueries.listPaginatedUnconfirmedTransactionsQuery(pagination)).futureValue

          actual should contain theSameElementsInOrderAs expected
      }
    }
  }

  "utxsFromTxs" should {
    "list unconfirmed transaction for transaction ids" in {
      forAll(Gen.listOf(unconfirmedTransactionEntityGen()), Gen.posNum[Int]) {
        case (unconfirmedTxs, txsCountToQuery) =>
          createTestData(unconfirmedTxs)

          val expected =
            Random
              .shuffle(unconfirmedTxs)
              .take(txsCountToQuery) //randomly select few
              .sortBy(_.lastSeen)(TimeStamp.ordering.reverse) //descending order order last_seen

          val transactionIdsToQuery = //shuffle input to ensure output is sorted based on last_seen
            Random.shuffle(expected.map(_.hash))

          val actual =
            run(UnconfirmedTransactionQueries.utxsFromTxs(transactionIdsToQuery)).futureValue

          actual should contain theSameElementsInOrderAs expected
      }
    }
  }

  "utxFromTxHash" should {
    "fetch unconfirmed transaction for a transaction id" in {
      forAll(Gen.listOf(unconfirmedTransactionEntityGen())) { unconfirmedTxs =>
        createTestData(unconfirmedTxs)

        unconfirmedTxs foreach { expected =>
          val actual =
            run(UnconfirmedTransactionQueries.utxFromTxHash(expected.hash)).futureValue

          actual should contain only expected
        }
      }
    }
  }
}
