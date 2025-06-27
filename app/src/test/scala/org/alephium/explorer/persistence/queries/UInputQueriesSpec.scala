// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumFutureSpec, GenCommon}
import org.alephium.explorer.GenCoreProtocol.transactionHashGen
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.{DatabaseFixtureForAll, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._

class UInputQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForAll with DBRunner {

  /** Clear [[UInputSchema]] table and persist new test data.
    */
  def createTestData(uinputs: Iterable[UInputEntity]): Unit = {
    run(UInputSchema.table.delete).futureValue
    val persistCount = run(UInputSchema.table ++= uinputs).futureValue
    persistCount should contain(uinputs.size)
    ()
  }

  "uinputsFromTxs" should {
    "return distinct hashes for address" in {
      forAll(uinputEntityWithDuplicateTxIdsAndAddressesGen()) { uinputs =>
        createTestData(uinputs)

        // randomly select some of the persisted test data
        // and expect only these to be returned by the query
        val generator =
          Gen.someOf(uinputs.map(_.txHash))

        forAll(generator) { txIds =>
          // For each iteration expect entities with txHashes to be the same as txIds
          val expected = uinputs.filter(uinput => txIds.contains(uinput.txHash))

          val actual =
            run(MempoolQueries.uinputsFromTxs(txIds)).futureValue

          actual should contain theSameElementsAs expected
        }
      }
    }
  }

  "uinputsFromTx" should {
    "return uinput entities ordered by uinput_order" in {
      forAll(uinputEntityWithDuplicateTxIdsAndAddressesGen()) { _uinputs =>
        // update uinputs to have incremental order so it's easier to test
        val uinputs =
          _uinputs.zipWithIndex map { case (uinput, index) =>
            uinput.copy(uinputOrder = index)
          }

        createTestData(uinputs)

        val txIds =
          uinputs.map(_.txHash)

        val txIdGen = // randomly pick a txId or generate a new one if the list is empty.
          GenCommon.pickOneOrGen(txIds)(transactionHashGen)

        forAll(txIdGen) { txId =>
          // expect only the uinputs with this txId.
          val expected =
            uinputs
              .filter(_.txHash == txId)
              .sortBy(_.uinputOrder)

          val actual =
            run(MempoolQueries.uinputsFromTx(txId)).futureValue

          // the result should be in expected order.
          actual should contain theSameElementsInOrderAs expected
        }
      }
    }
  }
}
