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

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumFutureSpec, GenCommon}
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.persistence.{DatabaseFixtureForAll, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._

class UOutputQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForAll with DBRunner {

  /**
    * Clear [[UOutputSchema]] table and persist new test data.
    */
  def createTestData(uoutput: Iterable[UOutputEntity]): Unit = {
    run(UOutputSchema.table.delete).futureValue
    val persistCount = run(UOutputSchema.table ++= uoutput).futureValue
    persistCount should contain(uoutput.size)
    ()
  }

  "uoutputsFromTxs" should {
    "return distinct hashes for address" in {
      forAll(uoutputEntityWithDuplicateTxIdsAndAddressesGen()) { uoutput =>
        createTestData(uoutput)

        //randomly select some of the TransactionIds from the
        //persisted test data and expect only these to be returned
        val txIdsGen =
          Gen.someOf(uoutput.map(_.txHash))

        forAll(txIdsGen) { txIds =>
          //For each iteration expect entities with txHashes to be the same as txIds
          val expected = uoutput.filter(uoutput => txIds.contains(uoutput.txHash))

          val actual =
            run(MempoolQueries.uoutputsFromTxs(txIds)).futureValue

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

        val txIdGen = //randomly pick a txId or generate a new one if the list is empty.
          GenCommon.pickOneOrGen(txIds)(transactionHashGen)

        forAll(txIdGen) { txId =>
          //expect only the uoutput with this txId.
          val expected =
            uoutputs
              .filter(_.txHash == txId)
              .sortBy(_.uoutputOrder)

          val actual =
            run(MempoolQueries.uoutputsFromTx(txId)).futureValue

          //the result should be in expected order.
          actual should contain theSameElementsInOrderAs expected
        }
      }
    }
  }
}
