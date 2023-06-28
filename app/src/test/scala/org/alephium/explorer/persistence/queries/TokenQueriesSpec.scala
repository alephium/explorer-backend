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

import org.alephium.explorer.{AlephiumFutureSpec, GroupSetting}
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.queries.TokenQueries
import org.alephium.explorer.persistence.queries.result.TxByTokenQR
import org.alephium.explorer.persistence.schema._

class TokenQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with DBRunner {

  "Token Queries" should {
    "list token transactions" in {
      forAll(Gen.listOfN(30, transactionPerTokenEntityGen()), tokenIdGen) {
        case (txPerTokens, token) =>
          run(TransactionPerTokenSchema.table.delete).futureValue
          run(TransactionPerTokenSchema.table ++= txPerTokens.map(_.copy(token = token))).futureValue

          val expected = txPerTokens
            .filter(_.mainChain)
            .map(tx => TxByTokenQR(tx.hash, tx.blockHash, tx.timestamp, tx.txOrder))
          val result =
            run(
              TokenQueries.listTokenTransactionsAction(
                token,
                Pagination.unsafe(1, txPerTokens.size))).futureValue

          result.size is expected.size
          result should contain allElementsOf expected
      }
    }

    "get token tx hashes by address query" in {
      forAll(Gen.listOfN(30, tokenTxPerAddressEntityGen()), addressGen, tokenIdGen) {
        case (txPerAddressTokens, address, token) =>
          run(TokenPerAddressSchema.table.delete).futureValue
          run(
            TokenPerAddressSchema.table ++= txPerAddressTokens.map(
              _.copy(address = address, token = token))).futureValue

          val expected = txPerAddressTokens
            .filter(_.mainChain)
            .map(tx => TxByTokenQR(tx.hash, tx.blockHash, tx.timestamp, tx.txOrder))
          val result =
            run(
              TokenQueries.getTokenTxHashesByAddressQuery(
                address,
                token,
                Pagination.unsafe(1, txPerAddressTokens.size))).futureValue

          result.size is expected.size
          result should contain allElementsOf expected
      }
    }

    //TODO Test data aren't coherent, we currently only test that query doesn't throw an error -_-'
    "list address tokens with balance" in {
      implicit val groupSetting: GroupSetting = GroupSetting(4)
      val testData                            = Gen.nonEmptyListOf(blockAndItsMainChainEntitiesGen()).sample.get
      val inputs                              = testData.flatMap(_._1.inputs)
      val tokenOutputs                        = testData.map(_._4)
      val addresses                           = tokenOutputs.map(_.address)
      val pagination                          = Pagination.unsafe(1, 10)

      run(InputSchema.table.delete).futureValue
      run(InputSchema.table ++= inputs)
      run(TokenOutputSchema.table.delete).futureValue
      run(TokenOutputSchema.table ++= tokenOutputs)

      addresses.foreach { address =>
        run(TokenQueries.listAddressTokensWithBalanceAction(address, pagination)).futureValue
      }
    }
  }
}
