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
import scala.jdk.CollectionConverters._

import io.vertx.core.buffer.Buffer
import org.scalacheck.Gen

import org.alephium.api.UtilJson._
import org.alephium.explorer.AlephiumActorSpecLike
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.persistence.dao.{BlockDao, MempoolDao}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.InputUpdateQueries
import org.alephium.json.Json._
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
      .getTransactionsByAddress(address, Pagination.unsafe(1, txLimit))
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
    val blockHash0 = blockHashGen.sample.get
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
      None,
      coinbase = false
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
                   coinbase = false,
                   None,
                   None)

    val block0 = defaultBlockEntity.copy(
      hash         = blockHash0,
      timestamp    = ts0,
      transactions = ArraySeq(tx0),
      outputs      = ArraySeq(output0)
    )

    val ts1        = TimeStamp.unsafe(1)
    val blockHash1 = blockHashGen.sample.get
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
      None,
      coinbase = false
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
                               coinbase = false,
                               None,
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
      gasPrice,
      scriptExecutionOk = true,
      coinbase          = false
    )

    val t1 = Transaction(
      tx1.hash,
      blockHash1,
      ts1,
      ArraySeq(
        Input(OutputRef(0, output0.key),
              None,
              Some(output0.txHash),
              Some(address0),
              Some(U256.One))),
      ArraySeq(AssetOutput(output1.hint, output1.key, U256.One, address1, None, None, None, None)),
      gasAmount1,
      gasPrice1,
      scriptExecutionOk = true,
      coinbase          = false
    )

    val res2 =
      TransactionService.getTransactionsByAddress(address0, Pagination.unsafe(1, 5)).futureValue

    res2 is ArraySeq(t1, t0)
  }

  "get only main chain transaction for an address in case of tx in two blocks (in case of reorg)" in new Fixture {

    forAll(blockHashGen, blockHashGen) {
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
          None,
          coinbase = false
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
                       coinbase = false,
                       None,
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
          .getTransactionsByAddress(address0, Pagination.unsafe(1, 5))
          .futureValue
          .size is 1 // was 2 in fb7127f

        TransactionService
          .getTransaction(tx.hash)
          .futureValue
          .get
          .asInstanceOf[AcceptedTransaction]
          .blockHash is blockHash0 // was sometime blockHash1 in fb7127f
    }
  }

  "fall back on mempool tx" in new Fixture {
    val utx = mempooltransactionGen.sample.get

    TransactionService.getTransaction(utx.hash).futureValue is None
    MempoolDao.insertMany(ArraySeq(utx)).futureValue
    TransactionService.getTransaction(utx.hash).futureValue is Some(PendingTransaction.from(utx))
  }

  "return mempool txs of an address" in new Fixture {
    forAll(addressGen, Gen.listOf(mempooltransactionGen)) {
      case (address, utxs) =>
        val updatedUtxs = utxs.map { utx =>
          utx.copy(inputs = utx.inputs.map { input =>
            input.copy(address = Some(address))
          })
        }

        MempoolDao.insertMany(updatedUtxs).futureValue

        val expected =
          updatedUtxs.filter(_.inputs.exists(_.address === Some(address)))

        TransactionService
          .listMempoolTransactionsByAddress(address)
          .futureValue should contain allElementsOf expected

        MempoolDao.removeMany(updatedUtxs.map(_.hash)).futureValue
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
            .asInstanceOf[AcceptedTransaction]
        transaction.outputs.map(_.key) is block.outputs
          .filter(_.txHash == tx.hash)
          .sortBy(_.outputOrder)
          .map(_.key)
      }
    }

    TransactionService
      .getTransactionsByAddress(address, Pagination.unsafe(1, Int.MaxValue))
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

  "export transactions by address" in new TxsByAddressFixture {
    forAll(Gen.choose(1, 4)) { batchSize =>
      val flowable = TransactionService
        .exportTransactionsByAddress(address, fromTs, toTs, batchSize, 8)

      val result: Seq[Buffer] =
        flowable.toList().blockingGet().asScala

      //TODO Check data format and not only the size

      result.size is ((transactions.size.toFloat / batchSize.toFloat).ceil.toInt + 1) //header

      //Checking the final csv has the correct number of lines
      val csvFile = result.map(_.toString()).mkString.split('\n')

      csvFile.size is (transactions.size + 1)
    }
  }

  "get amount history" in new TxsByAddressFixture {
    Seq[IntervalType](IntervalType.Hourly, IntervalType.Daily).foreach { intervalType =>
      val flowable = TransactionService
        .getAmountHistory(address, fromTs, toTs, intervalType, 8)

      val result: Seq[Buffer] =
        flowable.toList().blockingGet().asScala

      val amountHistory = read[ujson.Value](result.mkString)
      val history       = read[Seq[(Long, BigInteger)]](amountHistory("amountHistory"))

      val times = history.map(_._1)

      //Test that history is always ordered correctly
      times is times.sorted

    //TODO Test history amount value
    }
  }

  "has addres more txs than threshold" in new TxsByAddressFixture {
    TransactionService
      .hasAddressMoreTxsThan(address, fromTs, toTs, transactions.size)
      .futureValue is false
    TransactionService
      .hasAddressMoreTxsThan(address, fromTs, toTs, transactions.size - 1)
      .futureValue is true

    //Checking fromTs/toTs are taken into account
    val fromMs = fromTs.millis
    val toMs   = toTs.millis
    val diff   = (toMs - fromMs) / 2
    forAll(Gen.choose(fromTs.millis, fromTs.millis + diff).map(TimeStamp.unsafe),
           Gen.choose(toTs.millis - diff, toTs.millis).map(TimeStamp.unsafe)) {
      case (from, to) =>
        from <= to is true

        val txsNumber = transactions.count(tx => tx.timestamp >= from && tx.timestamp < to)

        TransactionService
          .hasAddressMoreTxsThan(address, from, to, txsNumber)
          .futureValue is false

        if (txsNumber != 0) {
          TransactionService
            .hasAddressMoreTxsThan(address, from, to, txsNumber - 1)
            .futureValue is true
        }
    }
  }

  trait Fixture {
    implicit val blockCache: BlockCache = TestBlockCache()

    val groupIndex = GroupIndex.unsafe(0)

    val defaultBlockEntity: BlockEntity =
      BlockEntity(
        hash         = blockHashGen.sample.get,
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

  trait TxsByAddressFixture extends Fixture {
    val address = addressGen.sample.get

    val halfDay = Duration.ofHoursUnsafe(12)
    val now     = TimeStamp.now()

    val blocks = Gen
      .listOfN(5, blockEntityGen(groupIndex, groupIndex))
      .map(_.zipWithIndex.map {
        case (block, index) =>
          val timestamp = now + (halfDay.timesUnsafe(index.toLong))
          block.copy(
            timestamp = timestamp,
            outputs   = block.outputs.map(_.copy(address = address, timestamp = timestamp)),
            inputs =
              block.inputs.map(_.copy(outputRefAddress = Some(address), timestamp = timestamp)),
            transactions = block.transactions.map { tx =>
              tx.copy(timestamp = timestamp)
            }
          )
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
    val timestamps   = transactions.map(_.timestamp).distinct
    val fromTs       = timestamps.head
    val toTs         = timestamps.last + Duration.ofMillisUnsafe(1)
  }
}
