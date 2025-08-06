// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumFutureSpec, GenCommon}
import org.alephium.explorer.GenCoreProtocol.transactionHashGen
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.{DatabaseFixtureForAll, TestDBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._

class UOutputQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForAll with TestDBRunner {

  /** Clear [[UOutputSchema]] table and persist new test data.
    */
  def createTestData(uoutput: Iterable[UOutputEntity]): Unit = {
    exec(UOutputSchema.table.delete)
    val persistCount = exec(UOutputSchema.table ++= uoutput)
    persistCount should contain(uoutput.size)
    ()
  }

  "uoutputsFromTxs" should {
    "return distinct hashes for address" in {
      forAll(uoutputEntityWithDuplicateTxIdsAndAddressesGen()) { uoutput =>
        createTestData(uoutput)

        // randomly select some of the TransactionIds from the
        // persisted test data and expect only these to be returned
        val txIdsGen =
          Gen.someOf(uoutput.map(_.txHash))

        forAll(txIdsGen) { txIds =>
          // For each iteration expect entities with txHashes to be the same as txIds
          val expected = uoutput.filter(uoutput => txIds.contains(uoutput.txHash))

          val actual =
            exec(MempoolQueries.uoutputsFromTxs(txIds))

          actual should contain theSameElementsAs expected
        }
      }
    }
  }

  "uoutputsFromTx" should {
    "return uoutput entities ordered by uoutput_order" in {
      forAll(uoutputEntityWithDuplicateTxIdsAndAddressesGen()) { uoutputs =>
        createTestData(uoutputs)

        val txIds =
          uoutputs.map(_.txHash)

        val txIdGen = // randomly pick a txId or generate a new one if the list is empty.
          GenCommon.pickOneOrGen(txIds)(transactionHashGen)

        forAll(txIdGen) { txId =>
          // expect only the uoutput with this txId.
          val expected =
            uoutputs
              .filter(_.txHash == txId)
              .sortBy(_.uoutputOrder)

          val actual =
            exec(MempoolQueries.uoutputsFromTx(txId))

          // the result should be in expected order.
          actual should contain theSameElementsInOrderAs expected
        }
      }
    }
  }
}
