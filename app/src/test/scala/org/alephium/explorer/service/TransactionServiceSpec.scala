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

import scala.concurrent.{ExecutionContext, Future}

import org.scalacheck.Gen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}

import org.alephium.explorer.{AlephiumSpec, Generators}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.persistence.dao.{BlockDao, TransactionDao, UnconfirmedTxDao}
import org.alephium.explorer.persistence.model._
import org.alephium.protocol.ALF
import org.alephium.util.{TimeStamp, U256}

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.DefaultArguments"))
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

  it should "handle huge alf number" in new Fixture {

    val amount = ALF.MaxALFValue.mulUnsafe(ALF.MaxALFValue)

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
    val startGas   = Gen.posNum[Int].sample.get
    val gasPrice   = amountGen.sample.get

    val tx0 = TransactionEntity(
      transactionHashGen.sample.get,
      blockHash0,
      ts0,
      startGas,
      gasPrice,
      0
    )

    val output0 =
      OutputEntity(blockHash0, tx0.hash, U256.One, address0, hashGen.sample.get, ts0, true, None)

    val block0 = BlockEntity(
      hash         = blockHash0,
      timestamp    = ts0,
      chainFrom    = groupIndex,
      chainTo      = groupIndex,
      height       = Height.unsafe(0),
      deps         = Seq.empty,
      transactions = Seq(tx0),
      inputs       = Seq.empty,
      outputs      = Seq(output0),
      true
    )

    val ts1        = TimeStamp.unsafe(1)
    val blockHash1 = blockEntryHashGen.sample.get
    val startGas1  = Gen.posNum[Int].sample.get
    val gasPrice1  = amountGen.sample.get
    val tx1 = TransactionEntity(
      transactionHashGen.sample.get,
      blockHash1,
      ts1,
      startGas1,
      gasPrice1,
      0
    )
    val input1 = InputEntity(blockHash1,
                             tx1.hash,
                             timestamp    = ts1,
                             scriptHint   = 0,
                             outputRefKey = output0.key,
                             None,
                             true)
    val output1 = OutputEntity(blockHash1,
                               tx1.hash,
                               U256.One,
                               address1,
                               hashGen.sample.get,
                               timestamp = ts1,
                               true,
                               None)

    val block1 = BlockEntity(
      hash         = blockHash1,
      timestamp    = ts1,
      chainFrom    = groupIndex,
      chainTo      = groupIndex,
      height       = Height.unsafe(1),
      deps         = Seq.empty,
      transactions = Seq(tx1),
      inputs       = Seq(input1),
      outputs      = Seq(output1),
      true
    )

    val blocks = Seq(block0, block1)

    Future.sequence(blocks.map(blockDao.insert)).futureValue

    val t0 = Transaction(
      tx0.hash,
      blockHash0,
      ts0,
      Seq.empty,
      Seq(Output(U256.One, address0, None, Some(tx1.hash))),
      startGas,
      gasPrice
    )

    val t1 = Transaction(
      tx1.hash,
      blockHash1,
      ts1,
      Seq(Input(Output.Ref(0, output0.key), None, tx0.hash, address0, U256.One)),
      Seq(Output(U256.One, address1, None, None)),
      startGas1,
      gasPrice1
    )

    val res =
      transactionService.getTransactionsByAddress(address0, Pagination.unsafe(0, 5)).futureValue

    res is Seq(t1, t0)
  }

  it should "fall back on unconfirmed tx" in new Fixture {
    val utx = utransactionGen.sample.get

    transactionService.getTransaction(utx.hash).futureValue is None
    utransactionDao.insertMany(Seq(utx)).futureValue
    transactionService.getTransaction(utx.hash).futureValue is Some(utx)
  }

  trait Fixture extends DatabaseFixture {
    val blockDao: BlockDao                     = BlockDao(databaseConfig)
    val transactionDao: TransactionDao         = TransactionDao(databaseConfig)
    val utransactionDao: UnconfirmedTxDao      = UnconfirmedTxDao(databaseConfig)
    val transactionService: TransactionService = TransactionService(transactionDao, utransactionDao)

    val groupIndex = GroupIndex.unsafe(0)
  }
}
