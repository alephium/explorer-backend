// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.util.Random

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.{DatabaseFixtureForAll, TestDBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.protocol.model.{Address, TransactionId}
import org.alephium.util.TimeStamp

class MempoolQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForAll with TestDBRunner {

  /** Setup tests data for table [[MempoolTransactionSchema]]
    *
    * @param mempoolTxs
    *   transaction to create a table for
    */
  def createTestData(mempoolTxs: Iterable[MempoolTransactionEntity]): Unit = {
    exec(MempoolTransactionSchema.table.delete)
    val persistCount = exec(MempoolTransactionSchema.table ++= mempoolTxs)
    persistCount should contain(mempoolTxs.size)
    ()
  }

  def createTestData(uinputs: Iterable[UInputEntity], uoutput: Iterable[UOutputEntity]): Unit = {
    exec(UInputSchema.table.delete)
    exec(UOutputSchema.table.delete)
    val inPersistCount  = exec(UInputSchema.table ++= uinputs)
    val outPersistCount = exec(UOutputSchema.table ++= uoutput)
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
        val actualHashes   = exec(MempoolQueries.listHashesQuery)

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
            exec(MempoolQueries.listPaginatedMempoolTransactionsQuery(pagination))

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
            exec(MempoolQueries.utxsFromTxs(transactionIdsToQuery))

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
            exec(MempoolQueries.utxFromTxHash(expected.hash))

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
            exec(MempoolQueries.listUTXHashesByAddress(address))

          // expect distinct transaction hashes
          val expected = expectedTx.distinct

          actual should contain theSameElementsAs expected
        }
      }
    }
  }
}
