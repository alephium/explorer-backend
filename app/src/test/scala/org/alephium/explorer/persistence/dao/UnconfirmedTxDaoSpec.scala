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

import scala.collection.immutable.ArraySeq

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenApiModel.{assetOutputGen, utransactionGen}
import org.alephium.explorer.GenCoreUtil.timestampGen
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.TransactionId

class UnconfirmedTxDaoSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with DBRunner {

  "insertMany" in {
    forAll(Gen.listOfN(5, utransactionGen)) { txs =>
      UnconfirmedTxDao.insertMany(txs).futureValue

      txs.foreach { tx =>
        val dbTx =
          run(UnconfirmedTxSchema.table.filter(_.hash === tx.hash).result).futureValue
        dbTx.size is 1
        dbTx.head.hash is tx.hash
        dbTx.head.chainFrom is tx.chainFrom
        dbTx.head.chainTo is tx.chainTo
        dbTx.head.gasAmount is tx.gasAmount
        dbTx.head.gasPrice is tx.gasPrice

        val inputs = run(UInputSchema.table.filter(_.txHash === tx.hash).result).futureValue
        inputs.size is tx.inputs.size
        inputs.foreach(input => tx.inputs.contains(input.toApi))

        val outputs =
          run(UOutputSchema.table.filter(_.txHash === tx.hash).result).futureValue
        outputs.size is tx.outputs.size
        outputs.foreach(output => tx.outputs.contains(output.toApi))
      }
    }
  }

  "get" in {
    forAll(utransactionGen) { utx =>
      UnconfirmedTxDao.insertMany(ArraySeq(utx)).futureValue

      UnconfirmedTxDao.get(utx.hash).futureValue is Some(utx)
    }
  }

  "get utx with multiple outputs with same address but different lock time. Issue #142 " in {
    forAll(Gen.choose(2, 6), assetOutputGen, utransactionGen) {
      case (outputSize, out, utx) =>
        //outputs with same address but different lockTime
        val outputs = ArraySeq.fill(outputSize)(out.copy(lockTime = Some(timestampGen.sample.get)))

        UnconfirmedTxDao.insertMany(ArraySeq(utx.copy(outputs = outputs))).futureValue

        UnconfirmedTxDao.get(utx.hash).futureValue.get.outputs.size is outputSize
    }
  }

  "removeMany" in {
    forAll(Gen.listOfN(5, utransactionGen)) { txs =>
      UnconfirmedTxDao.insertMany(txs).futureValue
      UnconfirmedTxDao.removeMany(txs.map(_.hash)).futureValue

      txs.foreach { tx =>
        UnconfirmedTxDao.get(tx.hash).futureValue is None
      }
    }
  }

  "listHashes" in {
    var hashes = Set.empty[TransactionId]
    forAll(Gen.listOfN(5, utransactionGen)) { txs =>
      UnconfirmedTxDao.insertMany(txs).futureValue
      hashes = hashes ++ txs.map(_.hash)
      UnconfirmedTxDao.listHashes().futureValue.toSet is hashes
    }
  }
}
