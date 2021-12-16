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

import org.scalatest.concurrent.ScalaFutures
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.explorer.{AlephiumSpec, Generators, Hash}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.{DatabaseFixture, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp, U256}

class TransactionQueriesSpec extends AlephiumSpec with ScalaFutures {

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  it should "compute locked balance" in new Fixture {
    import databaseConfig.profile.api._

    val output1 = output(address, ALPH.alph(1), None)
    val output2 =
      output(address, ALPH.alph(2), Some(TimeStamp.now().minusUnsafe(Duration.ofMinutesUnsafe(10))))
    val output3 = output(address, ALPH.alph(3), Some(TimeStamp.now().plusMinutesUnsafe(10)))
    val output4 = output(address, ALPH.alph(4), Some(TimeStamp.now().plusMinutesUnsafe(10)))

    run(queries.outputsTable ++= Seq(output1, output2, output3, output4)).futureValue

    val (total, locked) = run(queries.getBalanceAction(address)).futureValue

    total is ALPH.alph(10)
    locked is ALPH.alph(7)
  }

  it should "get balance should only return unpent outputs" in new Fixture {
    import databaseConfig.profile.api._

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val input1  = input(output2.hint, output2.key)

    run(queries.outputsTable ++= Seq(output1, output2)).futureValue
    run(queries.inputsTable += input1).futureValue

    val (total, _) = run(queries.getBalanceAction(address)).futureValue

    total is ALPH.alph(1)
  }

  trait Fixture extends DatabaseFixture with DBRunner with Generators {
    val config: DatabaseConfig[JdbcProfile] = databaseConfig

    class Queries(val config: DatabaseConfig[JdbcProfile])(
        implicit val executionContext: ExecutionContext)
        extends TransactionQueries

    val queries = new Queries(databaseConfig)

    val address = addressGen.sample.get
    val now     = TimeStamp.now()

    def output(address: Address, amount: U256, lockTime: Option[TimeStamp]): OutputEntity =
      OutputEntity(
        blockEntryHashGen.sample.get,
        transactionHashGen.sample.get,
        0,
        hashGen.sample.get,
        amount,
        address,
        now,
        true,
        lockTime,
        0
      )

    def input(hint: Int, outputRefKey: Hash): InputEntity =
      InputEntity(
        blockEntryHashGen.sample.get,
        transactionHashGen.sample.get,
        now,
        hint,
        outputRefKey,
        None,
        true,
        0
      )
  }
}
