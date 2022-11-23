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

package org.alephium.explorer.service

import java.math.BigInteger

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future

import akka.stream.scaladsl._
import io.vertx.core.buffer.Buffer
import org.scalacheck.Gen

import org.alephium.explorer.{AlephiumActorSpecLike, GroupSetting}
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.persistence.dao.{BlockDao, UnconfirmedTxDao}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.InputUpdateQueries
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.BlockHash
import org.alephium.util.{Duration, TimeStamp, U256}

@SuppressWarnings(
  Array("org.wartremover.warts.Var",
        "org.wartremover.warts.DefaultArguments",
        "org.wartremover.warts.AsInstanceOf"))
class TransactionServiceSpec extends AlephiumActorSpecLike with DatabaseFixtureForEach {

  "limit the number of transactions in address details" in new Fixture {

    val address = addressGen.sample.get

    val blocks = Gen
      .listOfN(20, blockEntityGen(groupIndex, groupIndex))
      .map(_.map { block =>
        block.copy(outputs = block.outputs.map(_.copy(address = address)))
      })
      .sample
      .get

    val txLimit = 5

    BlockDao.insertAll(blocks).futureValue
    Future
      .sequence(blocks.map { block =>
        for {
          _ <- databaseConfig.db.run(InputUpdateQueries.updateInputs())
          _ <- BlockDao.updateMainChainStatus(block.hash, true)
        } yield (())
      })
      .futureValue

    TransactionService
      .getTransactionsByAddressSQL(address, Pagination.unsafe(0, txLimit))
      .futureValue
      .size is txLimit
  }

  "handle huge alph number" in new Fixture {

    val amount = ALPH.MaxALPHValue.mulUnsafe(ALPH.MaxALPHValue)

    val block = blockEntityGen(groupIndex, groupIndex)
      .map { block =>
        block.copy(
          outputs = block.outputs.take(1).map(_.copy(amount = amount))
        )
      }
      .sample
      .get

    block.outputs.head.amount is amount

    BlockDao.insert(block).futureValue
    BlockDao.updateMainChainStatus(block.hash, true).futureValue
    databaseConfig.db.run(InputUpdateQueries.updateInputs()).futureValue

    val fetchedAmout =
      BlockDao
        .get(block.hash)
        .futureValue
        .get
        .transactions
        .flatMap(_.outputs.map(_.attoAlphAmount))
        .head
    fetchedAmout is amount
  }

  "get all transactions for an address even when outputs don't contain that address" in new Fixture {

    val address0 = addressGen.sample.get
    val address1 = addressGen.sample.get

    val ts0        = TimeStamp.unsafe(0)
    val blockHash0 = blockEntryHashGen.sample.get
    val gasAmount  = Gen.posNum[Int].sample.get
    val gasPrice   = amountGen.sample.get

    val tx0 = TransactionEntity(
      transactionHashGen.sample.get,
      blockHash0,
      ts0,
      groupIndex,
      groupIndex,
      gasAmount,
      gasPrice,
      0,
      true,
      true,
      None,
      None
    )

    val output0 =
      OutputEntity(blockHash0,
                   tx0.hash,
                   ts0,
                   OutputEntity.Asset,
                   0,
                   hashGen.sample.get,
                   U256.One,
                   address0,
                   None,
                   true,
                   None,
                   None,
                   0,
                   0,
                   None)

    val block0 = defaultBlockEntity.copy(
      hash         = blockHash0,
      timestamp    = ts0,
      transactions = ArraySeq(tx0),
      outputs      = ArraySeq(output0)
    )

    val ts1        = TimeStamp.unsafe(1)
    val blockHash1 = blockEntryHashGen.sample.get
    val gasAmount1 = Gen.posNum[Int].sample.get
    val gasPrice1  = amountGen.sample.get
    val tx1 = TransactionEntity(
      transactionHashGen.sample.get,
      blockHash1,
      ts1,
      groupIndex,
      groupIndex,
      gasAmount1,
      gasPrice1,
      0,
      true,
      true,
      None,
      None
    )
    val input1 = InputEntity(blockHash1,
                             tx1.hash,
                             timestamp    = ts1,
                             hint         = 0,
                             outputRefKey = output0.key,
                             None,
                             true,
                             0,
                             0,
                             None,
                             None,
                             None)
    val output1 = OutputEntity(blockHash1,
                               tx1.hash,
                               timestamp = ts1,
                               OutputEntity.Asset,
                               0,
                               hashGen.sample.get,
                               U256.One,
                               address1,
                               None,
                               true,
                               None,
                               None,
                               0,
                               0,
                               None)

    val block1 = defaultBlockEntity.copy(
      hash         = blockHash1,
      timestamp    = ts1,
      height       = Height.unsafe(1),
      transactions = ArraySeq(tx1),
      inputs       = ArraySeq(input1),
      outputs      = ArraySeq(output1),
      deps         = ArraySeq.fill(2 * groupSetting.groupNum - 1)(BlockHash.generate)
    )

    val blocks = ArraySeq(block0, block1)

    Future.sequence(blocks.map(BlockDao.insert)).futureValue
    databaseConfig.db.run(InputUpdateQueries.updateInputs()).futureValue
    FinalizerService.finalizeOutputs().futureValue

    val t0 = Transaction(
      tx0.hash,
      blockHash0,
      ts0,
      ArraySeq.empty,
      ArraySeq(
        AssetOutput(output0.hint,
                    output0.key,
                    U256.One,
                    address0,
                    None,
                    None,
                    None,
                    Some(tx1.hash))),
      gasAmount,
      gasPrice
    )

    val t1 = Transaction(
      tx1.hash,
      blockHash1,
      ts1,
      ArraySeq(Input(OutputRef(0, output0.key), None, Some(address0), Some(U256.One))),
      ArraySeq(AssetOutput(output1.hint, output1.key, U256.One, address1, None, None, None, None)),
      gasAmount1,
      gasPrice1
    )

    val res2 =
      TransactionService.getTransactionsByAddressSQL(address0, Pagination.unsafe(0, 5)).futureValue

    res2 is ArraySeq(t1, t0)
  }

  "get only main chain transaction for an address in case of tx in two blocks (in case of reorg)" in new Fixture {

    forAll(blockEntryHashGen, blockEntryHashGen) {
      case (blockHash0, blockHash1) =>
        val address0 = addressGen.sample.get

        val ts0 = TimeStamp.unsafe(0)

        val tx = TransactionEntity(
          transactionHashGen.sample.get,
          blockHash0,
          ts0,
          groupIndex,
          groupIndex,
          Gen.posNum[Int].sample.get,
          amountGen.sample.get,
          0,
          true,
          true,
          None,
          None
        )

        val output0 =
          OutputEntity(blockHash0,
                       tx.hash,
                       ts0,
                       OutputEntity.Asset,
                       0,
                       hashGen.sample.get,
                       U256.One,
                       address0,
                       None,
                       true,
                       None,
                       None,
                       0,
                       0,
                       None)

        val block0 = defaultBlockEntity.copy(
          hash         = blockHash0,
          timestamp    = ts0,
          transactions = ArraySeq(tx),
          outputs      = ArraySeq(output0)
        )

        val ts1 = TimeStamp.unsafe(1)
        val block1 = block0.copy(
          hash      = blockHash1,
          timestamp = ts1,
          transactions = block0.transactions.map(
            _.copy(blockHash = blockHash1, timestamp = ts1, mainChain = false)),
          inputs =
            block0.inputs.map(_.copy(blockHash = blockHash1, timestamp = ts1, mainChain = false)),
          outputs =
            block0.outputs.map(_.copy(blockHash = blockHash1, timestamp = ts1, mainChain = false)),
          mainChain = false
        )

        val blocks = ArraySeq(block0, block1)

        Future.sequence(blocks.map(BlockDao.insert)).futureValue
        databaseConfig.db.run(InputUpdateQueries.updateInputs()).futureValue

        TransactionService
          .getTransactionsByAddressSQL(address0, Pagination.unsafe(0, 5))
          .futureValue
          .size is 1 // was 2 in fb7127f

        TransactionService
          .getTransaction(tx.hash)
          .futureValue
          .get
          .asInstanceOf[ConfirmedTransaction]
          .blockHash is blockHash0 // was sometime blockHash1 in fb7127f
    }
  }

  "fall back on unconfirmed tx" in new Fixture {
    val utx = utransactionGen.sample.get

    TransactionService.getTransaction(utx.hash).futureValue is None
    UnconfirmedTxDao.insertMany(ArraySeq(utx)).futureValue
    TransactionService.getTransaction(utx.hash).futureValue is Some(utx)
  }

  "return unconfirmed txs of an address" in new Fixture {
    forAll(addressGen, Gen.listOf(utransactionGen)) {
      case (address, utxs) =>
        val updatedUtxs = utxs.map { utx =>
          utx.copy(inputs = utx.inputs.map { input =>
            input.copy(address = Some(address))
          })
        }

        UnconfirmedTxDao.insertMany(updatedUtxs).futureValue

        val expected =
          updatedUtxs.filter(_.inputs.exists(_.address === Some(address)))

        TransactionService
          .listUnconfirmedTransactionsByAddress(address)
          .futureValue should contain allElementsOf expected

        UnconfirmedTxDao.removeMany(updatedUtxs.map(_.hash)).futureValue
    }
  }

  "preserve outputs order" in new Fixture {

    val address = addressGen.sample.get

    val blocks = Gen
      .listOfN(20, blockEntityGen(groupIndex, groupIndex))
      .map(_.map { block =>
        block.copy(outputs = block.outputs.map(_.copy(address = address)))
      })
      .sample
      .get

    val outputs = blocks.flatMap(_.outputs)

    Future.sequence(blocks.map(BlockDao.insert)).futureValue
    Future
      .sequence(blocks.map(block => BlockDao.updateMainChainStatus(block.hash, true)))
      .futureValue

    blocks.foreach { block =>
      block.transactions.map { tx =>
        val transaction =
          TransactionService
            .getTransaction(tx.hash)
            .futureValue
            .get
            .asInstanceOf[ConfirmedTransaction]
        transaction.outputs.map(_.key) is block.outputs
          .filter(_.txHash == tx.hash)
          .sortBy(_.outputOrder)
          .map(_.key)
      }
    }

    TransactionService
      .getTransactionsByAddressSQL(address, Pagination.unsafe(0, Int.MaxValue))
      .futureValue
      .map { transaction =>
        transaction.outputs.map(_.key) is outputs
          .filter(_.txHash == transaction.hash)
          .sortBy(_.outputOrder)
          .map(_.key)
      }
  }

  "preserve inputs order" in new Fixture {
    //TODO Test this please
    //We need to generate a coherent blockflow, otherwise the queries can't match the inputs with outputs

  }

  "get output ref's transaction" in new Fixture {

    val blocks = Gen
      .listOfN(20, blockEntityGen(groupIndex, groupIndex))
      .sample
      .get

    val outputRefKeys = blocks.flatMap(_.inputs.map(_.outputRefKey))

    Future.sequence(blocks.map(BlockDao.insert)).futureValue
    Future
      .sequence(blocks.map(block => BlockDao.updateMainChainStatus(block.hash, true)))
      .futureValue

    outputRefKeys.foreach { outputRefKey =>
      val tx = TransactionService
        .getOutputRefTransaction(outputRefKey)
        .futureValue
        .asInstanceOf[Option[ConfirmedTransaction]]

      //TODO Same as previous test, we need to generate a coherent blockflow.
      tx is None
    }
  }

  "check active address" in new Fixture {

    val address  = addressGen.sample.get
    val address2 = addressGen.sample.get

    val block =
      blockEntityGen(groupIndex, groupIndex)
        .map { block =>
          block.copy(outputs = block.outputs.map(_.copy(address = address)))
        }
        .sample
        .get

    BlockDao.insertAll(ArraySeq(block)).futureValue

    TransactionService.areAddressesActive(ArraySeq(address, address2)).futureValue is ArraySeq(
      true,
      false)
  }

  "export transactions by address" in new Fixture {

    val address = addressGen.sample.get

    val blocks = Gen
      .listOfN(5, blockEntityGen(groupIndex, groupIndex))
      .map(_.map { block =>
        block.copy(outputs = block.outputs.map(_.copy(address = address)))
      })
      .sample
      .get

    BlockDao.insertAll(blocks).futureValue
    Future
      .sequence(blocks.map { block =>
        for {
          _ <- databaseConfig.db.run(InputUpdateQueries.updateInputs())
          _ <- BlockDao.updateMainChainStatus(block.hash, true)
        } yield (())
      })
      .futureValue

    val transactions = blocks.flatMap(_.transactions).sortBy(_.timestamp)
    val timestamps   = transactions.map(_.timestamp)
    val fromTs       = timestamps.head
    val toTs         = timestamps.last + Duration.ofMillisUnsafe(1)

    forAll(Gen.choose(1, 4)) { batchSize =>
      val publisher = TransactionService
        .exportTransactionsByAddress(address, fromTs, toTs, ExportType.CSV, batchSize)

      val result: Seq[Buffer] =
        Source.fromPublisher(publisher).runWith(Sink.seq).futureValue

      //TODO Check data format and not only the size

      result.size is ((transactions.size.toFloat / batchSize.toFloat).ceil.toInt + 1) //header

      //Checking the final csv has the correct number of lines
      val csvFile = result.map(_.toString()).mkString.split('\n')

      csvFile.size is (transactions.size + 1)
    }
  }

  trait Fixture {
    implicit val groupSetting: GroupSetting = groupSettingGen.sample.get
    implicit val blockCache: BlockCache     = TestBlockCache()

    val groupIndex = GroupIndex.unsafe(0)

    val defaultBlockEntity: BlockEntity =
      BlockEntity(
        hash         = blockEntryHashGen.sample.get,
        timestamp    = TimeStamp.unsafe(0),
        chainFrom    = groupIndex,
        chainTo      = groupIndex,
        height       = Height.unsafe(0),
        deps         = ArraySeq.empty,
        transactions = ArraySeq.empty,
        inputs       = ArraySeq.empty,
        outputs      = ArraySeq.empty,
        true,
        nonce        = bytesGen.sample.get,
        version      = 1.toByte,
        depStateHash = hashGen.sample.get,
        txsHash      = hashGen.sample.get,
        target       = bytesGen.sample.get,
        hashrate     = BigInteger.ZERO
      )

  }
}
