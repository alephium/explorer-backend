// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.dao

import scala.collection.immutable.ArraySeq

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenApiModel.{assetOutputGen, mempooltransactionGen}
import org.alephium.explorer.GenCoreUtil.timestampGen
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.TransactionId

class MempoolTransactionDaoSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with DBRunner {

  "insertMany" in {
    forAll(Gen.listOfN(5, mempooltransactionGen)) { txs =>
      MempoolDao.insertMany(txs).futureValue

      txs.foreach { tx =>
        val dbTx =
          run(MempoolTransactionSchema.table.filter(_.hash === tx.hash).result).futureValue
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
    forAll(mempooltransactionGen) { utx =>
      MempoolDao.insertMany(ArraySeq(utx)).futureValue

      MempoolDao.get(utx.hash).futureValue is Some(utx)
    }
  }

  "get utx with multiple outputs with same address but different lock time. Issue #142 " in {
    forAll(Gen.choose(2, 6), assetOutputGen, mempooltransactionGen) { case (outputSize, out, utx) =>
      // outputs with same address but different lockTime
      val outputs = ArraySeq.fill(outputSize)(out.copy(lockTime = Some(timestampGen.sample.get)))

      MempoolDao.insertMany(ArraySeq(utx.copy(outputs = outputs))).futureValue

      MempoolDao.get(utx.hash).futureValue.get.outputs.size is outputSize
    }
  }

  "removeMany" in {
    forAll(Gen.listOfN(5, mempooltransactionGen)) { txs =>
      MempoolDao.insertMany(txs).futureValue
      MempoolDao.removeMany(txs.map(_.hash)).futureValue

      txs.foreach { tx =>
        MempoolDao.get(tx.hash).futureValue is None
      }
    }
  }

  "listHashes" in {
    var hashes = Set.empty[TransactionId]
    forAll(Gen.listOfN(5, mempooltransactionGen)) { txs =>
      MempoolDao.insertMany(txs).futureValue
      hashes = hashes ++ txs.map(_.hash)
      MempoolDao.listHashes().futureValue.toSet is hashes
    }
  }
}
