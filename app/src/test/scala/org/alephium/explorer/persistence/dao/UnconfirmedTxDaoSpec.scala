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

package org.alephium.explorer.persistence.dao

import scala.concurrent.ExecutionContext

import org.scalacheck.Gen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.Generators
import org.alephium.explorer.api.model.Transaction
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.persistence.DBRunner
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._

class UnconfirmedTxDaoSpec extends AlephiumSpec with ScalaFutures with Generators with Eventually {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  it should "insertMany" in new Fixture {
    forAll(Gen.listOfN(5, utransactionGen)) { txs =>
      utxDao.insertMany(txs).futureValue

      txs.foreach { tx =>
        val dbTx =
          runAction(UnconfirmedTxSchema.table.filter(_.hash === tx.hash).result).futureValue
        dbTx.size is 1
        dbTx.head.hash is tx.hash
        dbTx.head.chainFrom is tx.chainFrom
        dbTx.head.chainTo is tx.chainTo
        dbTx.head.gasAmount is tx.gasAmount
        dbTx.head.gasPrice is tx.gasPrice

        val inputs = runAction(UInputSchema.table.filter(_.txHash === tx.hash).result).futureValue
        inputs.size is tx.inputs.size
        inputs.foreach(input => tx.inputs.contains(input.toApi))

        val outputs =
          runAction(UOutputSchema.table.filter(_.txHash === tx.hash).result).futureValue
        outputs.size is tx.outputs.size
        outputs.foreach(output => tx.outputs.contains(output.toApi))
      }
    }
  }

  it should "get" in new Fixture {
    forAll(utransactionGen) { utx =>
      utxDao.insertMany(Seq(utx)).futureValue

      utxDao.get(utx.hash).futureValue is Some(utx)
    }
  }

  it should "removeMany" in new Fixture {
    forAll(Gen.listOfN(5, utransactionGen)) { txs =>
      utxDao.insertMany(txs).futureValue
      utxDao.removeMany(txs.map(_.hash)).futureValue

      txs.foreach { tx =>
        utxDao.get(tx.hash).futureValue is None
      }
    }
  }

  it should "listHashes" in new Fixture {
    var hashes = Set.empty[Transaction.Hash]
    forAll(Gen.listOfN(5, utransactionGen)) { txs =>
      utxDao.insertMany(txs).futureValue
      hashes = hashes ++ txs.map(_.hash)
      utxDao.listHashes().futureValue.toSet is hashes
    }
  }

  trait Fixture extends DatabaseFixture with DBRunner {
    val utxDao = UnconfirmedTxDao(databaseConfig)
  }
}
