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

import scala.concurrent.ExecutionContext

import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Minutes, Span}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.queries.TokenQueries
import org.alephium.explorer.persistence.queries.result.TxByAddressQR
import org.alephium.explorer.persistence.schema._

class TokenQueriesSpec
    extends AlephiumSpec
    with DatabaseFixtureForEach
    with DBRunner
    with ScalaFutures {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  "Token Queries" should {
    "list token transactions" in {
      forAll(Gen.listOfN(30, transactionPerTokenEntityGen()), hashGen) {
        case (txPerTokens, token) =>
          run(TransactionPerTokenSchema.table.delete).futureValue
          run(TransactionPerTokenSchema.table ++= txPerTokens.map(_.copy(token = token))).futureValue

          val expected = txPerTokens
            .filter(_.mainChain)
            .map(tx => TxByAddressQR(tx.hash, tx.blockHash, tx.timestamp, tx.txOrder))
          val result =
            run(TokenQueries.listTokenTransactionsAction(token, 0, txPerTokens.size)).futureValue

          result.size is expected.size
          result should contain allElementsOf expected
      }
    }

    "get token tx hashes by address query" in {
      forAll(Gen.listOfN(30, tokenTxPerAddressEntityGen()), addressGen, hashGen) {
        case (txPerAddressTokens, address, token) =>
          run(TokenPerAddressSchema.table.delete).futureValue
          run(
            TokenPerAddressSchema.table ++= txPerAddressTokens.map(
              _.copy(address = address, token = token))).futureValue

          val expected = txPerAddressTokens
            .filter(_.mainChain)
            .map(tx => TxByAddressQR(tx.hash, tx.blockHash, tx.timestamp, tx.txOrder))
          val result =
            run(
              TokenQueries.getTokenTxHashesByAddressQuery(address,
                                                          token,
                                                          0,
                                                          txPerAddressTokens.size)).futureValue

          result.size is expected.size
          result should contain allElementsOf expected
      }
    }
  }
}
