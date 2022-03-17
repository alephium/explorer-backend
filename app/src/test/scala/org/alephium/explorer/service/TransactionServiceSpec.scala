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

import scala.concurrent.{ExecutionContext, Future}

import org.scalacheck.Gen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}

import org.alephium.explorer.{AlephiumSpec, BlockHash, Generators}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.persistence.dao.{BlockDao, TransactionDao, UnconfirmedTxDao}
import org.alephium.explorer.persistence.model._
import org.alephium.protocol.ALPH
import org.alephium.util.{TimeStamp, U256}

@SuppressWarnings(
  Array("org.wartremover.warts.Var",
        "org.wartremover.warts.DefaultArguments",
        "org.wartremover.warts.AsInstanceOf"))
class TransactionServiceSpec
    extends AlephiumSpec
    with Generators
    with ScalaFutures
    with Eventually {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  it should "limit the number of transactions in address details" in new Fixture {

    val address = addressGen.sample.get

    val blocks = Gen
      .listOfN(20, blockEntityGen(groupIndex, groupIndex, None))
      .map(_.map { block =>
        block.copy(outputs = block.outputs.map(_.copy(address = address)))
      })
      .sample
      .get

    val txLimit = 5

    Future.sequence(blocks.map(blockDao.insert)).futureValue
    Future
      .sequence(blocks.map(block => blockDao.updateMainChainStatus(block.hash, true)))
      .futureValue

    transactionService
      .getTransactionsByAddress(address, Pagination.unsafe(0, txLimit))
      .futureValue
      .size is txLimit
  }

  it should "handle huge alph number" in new Fixture {

    val amount = ALPH.MaxALPHValue.mulUnsafe(ALPH.MaxALPHValue)

    val block = blockEntityGen(groupIndex, groupIndex, None)
      .map { block =>
        block.copy(
          outputs = block.outputs.take(1).map(_.copy(amount = amount))
        )
      }
      .sample
      .get

    block.outputs.head.amount is amount

    blockDao.insert(block).futureValue
    blockDao.updateMainChainStatus(block.hash, true).futureValue

    val fetchedAmout =
      blockDao.get(block.hash).futureValue.get.transactions.flatMap(_.outputs.map(_.amount)).head
    fetchedAmout is amount
  }

  it should "get all transactions for an address even when outputs don't contain that address" in new Fixture {

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
      true
    )

    val output0 =
      OutputEntity(blockHash0,
                   tx0.hash,
                   ts0,
                   0,
                   hashGen.sample.get,
                   U256.One,
                   address0,
                   true,
                   None,
                   0,
                   0)

    val block0 = defaultBlockEntity.copy(
      hash         = blockHash0,
      timestamp    = ts0,
      transactions = Seq(tx0),
      outputs      = Seq(output0)
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
      true
    )
    val input1 = InputEntity(blockHash1,
                             tx1.hash,
                             timestamp    = ts1,
                             hint         = 0,
                             outputRefKey = output0.key,
                             None,
                             true,
                             0,
                             0)
    val output1 = OutputEntity(blockHash1,
                               tx1.hash,
                               timestamp = ts1,
                               0,
                               hashGen.sample.get,
                               U256.One,
                               address1,
                               true,
                               None,
                               0,
                               0)

    val block1 = defaultBlockEntity.copy(
      hash         = blockHash1,
      timestamp    = ts1,
      height       = Height.unsafe(1),
      transactions = Seq(tx1),
      inputs       = Seq(input1),
      outputs      = Seq(output1),
      deps         = Seq.fill(2 * groupNum - 1)(new BlockEntry.Hash(BlockHash.generate))
    )

    val blocks = Seq(block0, block1)

    Future.sequence(blocks.map(blockDao.insert)).futureValue
    val inputsToUpdate =
      Future.sequence(blocks.map(blockDao.updateTransactionPerAddress)).futureValue.flatten
    blockDao.updateInputs(inputsToUpdate).futureValue

    val t0 = Transaction(
      tx0.hash,
      blockHash0,
      ts0,
      Seq.empty,
      Seq(Output(output0.hint, output0.key, U256.One, address0, None, Some(tx1.hash))),
      gasAmount,
      gasPrice
    )

    val t1 = Transaction(
      tx1.hash,
      blockHash1,
      ts1,
      Seq(Input(OutputRef(0, output0.key), None, tx0.hash, address0, U256.One)),
      Seq(Output(output1.hint, output1.key, U256.One, address1, None, None)),
      gasAmount1,
      gasPrice1
    )

    val res =
      transactionService.getTransactionsByAddress(address0, Pagination.unsafe(0, 5)).futureValue

    val res2 =
      transactionService.getTransactionsByAddressSQL(address0, Pagination.unsafe(0, 5)).futureValue

    res is Seq(t1, t0)
    res2 is Seq(t1, t0)
  }

  it should "get only main chain transaction for an address in case of tx in two blocks (in case of reorg)" in new Fixture {

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
          true
        )

        val output0 =
          OutputEntity(blockHash0,
                       tx.hash,
                       ts0,
                       0,
                       hashGen.sample.get,
                       U256.One,
                       address0,
                       true,
                       None,
                       0,
                       0)

        val block0 = defaultBlockEntity.copy(
          hash         = blockHash0,
          timestamp    = ts0,
          transactions = Seq(tx),
          outputs      = Seq(output0)
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

        val blocks = Seq(block0, block1)

        Future.sequence(blocks.map(blockDao.insert)).futureValue

        transactionService
          .getTransactionsByAddress(address0, Pagination.unsafe(0, 5))
          .futureValue
          .size is 1 // was 2 in fb7127f

        transactionService
          .getTransaction(tx.hash)
          .futureValue
          .get
          .asInstanceOf[Transaction]
          .blockHash is blockHash0 // was sometime blockHash1 in fb7127f
    }
  }

  it should "fall back on unconfirmed tx" in new Fixture {
    val utx = utransactionGen.sample.get

    transactionService.getTransaction(utx.hash).futureValue is None
    utransactionDao.insertMany(Seq(utx)).futureValue
    transactionService.getTransaction(utx.hash).futureValue is Some(utx)
  }

  it should "preserve outputs order" in new Fixture {

    val address = addressGen.sample.get

    val blocks = Gen
      .listOfN(20, blockEntityGen(groupIndex, groupIndex, None))
      .map(_.map { block =>
        block.copy(outputs = block.outputs.map(_.copy(address = address)))
      })
      .sample
      .get

    val outputs = blocks.flatMap(_.outputs)

    Future.sequence(blocks.map(blockDao.insert)).futureValue
    Future
      .sequence(blocks.map(block => blockDao.updateMainChainStatus(block.hash, true)))
      .futureValue

    blocks.foreach { block =>
      block.transactions.map { tx =>
        val transaction =
          transactionService.getTransaction(tx.hash).futureValue.get.asInstanceOf[Transaction]
        transaction.outputs.map(_.key) is block.outputs
          .filter(_.txHash == tx.hash)
          .sortBy(_.order)
          .map(_.key)
      }
    }

    transactionService
      .getTransactionsByAddress(address, Pagination.unsafe(0, Int.MaxValue))
      .futureValue
      .map { transaction =>
        transaction.outputs.map(_.key) is outputs
          .filter(_.txHash == transaction.hash)
          .sortBy(_.order)
          .map(_.key)
      }
  }

  it should "preserve inputs order" in new Fixture {
    //TODO Test this please
    //We need to generate a coherent blockflow, otherwise the queries can't match the inputs with outputs

  }

  trait Fixture extends DatabaseFixture {
    val blockDao: BlockDao                     = BlockDao(groupNum, databaseConfig)
    val transactionDao: TransactionDao         = TransactionDao(databaseConfig)
    val utransactionDao: UnconfirmedTxDao      = UnconfirmedTxDao(databaseConfig)
    val transactionService: TransactionService = TransactionService(transactionDao, utransactionDao)

    val groupIndex = GroupIndex.unsafe(0)

    val defaultBlockEntity: BlockEntity =
      BlockEntity(
        hash         = blockEntryHashGen.sample.get,
        timestamp    = TimeStamp.unsafe(0),
        chainFrom    = groupIndex,
        chainTo      = groupIndex,
        height       = Height.unsafe(0),
        deps         = Seq.empty,
        transactions = Seq.empty,
        inputs       = Seq.empty,
        outputs      = Seq.empty,
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
