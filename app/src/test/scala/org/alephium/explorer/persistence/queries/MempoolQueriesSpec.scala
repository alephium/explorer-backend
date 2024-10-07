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
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.{DatabaseFixtureForAll, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.protocol.model.{Address, TransactionId}
import org.alephium.util.TimeStamp

class MempoolQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForAll with DBRunner {

  /** Setup tests data for table [[MempoolTransactionSchema]]
    *
    * @param mempoolTxs
    *   transaction to create a table for
    */
  def createTestData(mempoolTxs: Iterable[MempoolTransactionEntity]): Unit = {
    run(MempoolTransactionSchema.table.delete).futureValue
    val persistCount = run(MempoolTransactionSchema.table ++= mempoolTxs).futureValue
    persistCount should contain(mempoolTxs.size)
    ()
  }

  def createTestData(uinputs: Iterable[UInputEntity], uoutput: Iterable[UOutputEntity]): Unit = {
    run(UInputSchema.table.delete).futureValue
    run(UOutputSchema.table.delete).futureValue
    val inPersistCount  = run(UInputSchema.table ++= uinputs).futureValue
    val outPersistCount = run(UOutputSchema.table ++= uoutput).futureValue
    inPersistCount should contain(uinputs.size)
    outPersistCount should contain(uoutput.size)
    ()
  }

  "listHashesQuery" should {
    "list mempool transaction hashes" in {
      forAll(Gen.listOf(mempoolTransactionEntityGen())) { mempoolTxs =>
        // setup test data
        createTestData(mempoolTxs)

        // fetch expected and actual data
        val expectedHashes = mempoolTxs.map(_.hash)
        val actualHashes   = run(MempoolQueries.listHashesQuery).futureValue

        actualHashes should contain theSameElementsAs expectedHashes
      }
    }
  }

  "listPaginatedMempoolTransactionsQuery" should {
    "list paginated mempool transactions" in {
      forAll(paginationDataGen(Gen.listOf(mempoolTransactionEntityGen()))) {
        case (mempoolTxs, pagination) =>
          createTestData(mempoolTxs)

          val expected =
            mempoolTxs
              .sortBy(_.lastSeen)(TimeStamp.ordering.reverse) // descending order order last_seen
              .slice(pagination.offset, pagination.offset + pagination.limit) // pagination

          val actual =
            run(MempoolQueries.listPaginatedMempoolTransactionsQuery(pagination)).futureValue

          actual should contain theSameElementsInOrderAs expected
      }
    }
  }

  "utxsFromTxs" should {
    "list mempool transaction for transaction ids" in {
      forAll(Gen.listOf(mempoolTransactionEntityGen()), Gen.posNum[Int]) {
        case (mempoolTxs, txsCountToQuery) =>
          createTestData(mempoolTxs)

          val expected =
            Random
              .shuffle(mempoolTxs)
              .take(txsCountToQuery)                          // randomly select few
              .sortBy(_.lastSeen)(TimeStamp.ordering.reverse) // descending order order last_seen

          val transactionIdsToQuery = // shuffle input to ensure output is sorted based on last_seen
            Random.shuffle(expected.map(_.hash))

          val actual =
            run(MempoolQueries.utxsFromTxs(transactionIdsToQuery)).futureValue

          actual should contain theSameElementsInOrderAs expected
      }
    }
  }

  "utxFromTxHash" should {
    "fetch mempool transaction for a transaction id" in {
      forAll(Gen.listOf(mempoolTransactionEntityGen())) { mempoolTxs =>
        createTestData(mempoolTxs)

        mempoolTxs foreach { expected =>
          val actual =
            run(MempoolQueries.utxFromTxHash(expected.hash)).futureValue

          actual should contain only expected
        }
      }
    }
  }

  "listUTXHashesByAddress" should {
    "return distinct hashes for address" in {
      forAll(uInOutEntityWithDuplicateTxIdsAndAddressesGen()) { case (uinputs, uoutputs) =>
        createTestData(uinputs, uoutputs)

        val addressAndExpectedTx: Map[Address, List[TransactionId]] =
          (uinputs
            .map { input =>
              input.address match {
                case Some(address) =>
                  (address, Some(input.txHash))
                case None =>
                  (addressGen.sample.get, None)
              }
            } ++ uoutputs.map { output =>
            (output.address, Some(output.txHash))
          }).groupBy { case (address, _) => address }
            .map { case (address, entities) =>
              (address, entities.map { case (_, txHashes) => txHashes }.flatten)
            }

        addressAndExpectedTx foreachEntry { case (address, expectedTx) =>
          val actual =
            run(MempoolQueries.listUTXHashesByAddress(address)).futureValue

          // expect distinct transaction hashes
          val expected = expectedTx.distinct

          actual should contain theSameElementsAs expected
        }
      }
    }
  }
}
