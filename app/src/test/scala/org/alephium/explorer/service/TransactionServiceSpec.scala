// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

//scalastyle:off file.size.limit
package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

import io.vertx.core.buffer.Buffer
import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache._
import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.dao.{BlockDao, MempoolDao}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.model.AppState._
import org.alephium.explorer.persistence.queries._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.util.AddressUtil
import org.alephium.explorer.util.UtxoUtil._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{ChainIndex, GroupIndex, UnsignedTransaction}
import org.alephium.serde._
import org.alephium.util.{Duration, Hex, TimeStamp}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
    "org.wartremover.warts.DefaultArguments",
    "org.wartremover.warts.AsInstanceOf"
  )
)
class TransactionServiceSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with TestDBRunner {

  "limit the number of transactions in address details" in new TxsByAddressFixture {

    val txLimit = 5

    TransactionService
      .getTransactionsByAddress(address, Pagination.unsafe(1, txLimit))
      .futureValue
      .size is txLimit
  }

  "handle huge alph number" in new Fixture {

    val amount = ALPH.MaxALPHValue.mulUnsafe(ALPH.MaxALPHValue)

    val block = blockEntityUpdatedGen(chainIndex) { block =>
      block.copy(
        outputs = block.outputs.take(1).map(_.copy(amount = amount))
      )
    }.sample.get

    block.outputs.head.amount is amount

    BlockDao.insert(block).futureValue
    BlockDao.updateMainChainStatus(block.hash, true).futureValue
    exec(InputUpdateQueries.updateInputs())

    val fetchedAmout =
      BlockDao
        .getTransactions(block.hash, Pagination.unsafe(1, 1000))
        .futureValue
        .flatMap(_.outputs.map(_.attoAlphAmount))
        .head
    fetchedAmout is amount
  }

  "convert unsigned tx bytes into a transaction" in new Fixture {
    val raw =
      "00040080004e20c1174876e8000137a44447cfff0c6c3951889e73ae1a3b0b1b2bb284cfb01e77cd910bf17cda8f3dcee82b000381818e63bd9e35a5489b52a430accefc608fd60aa2c7c0d1b393b5239aedf6b003c41bc16d674ec8000000622990ad7be0a3d163562c10fd7985ef40a3e41857e7a1583406a785efc9273a00000000000000000000c429a2241af62c00000000dd2354976f12629cfbc141e16f3592927f06c78bdc27d3f7a429602cc27d1200000000000000000000c6d3b40169eefd092ce00000bee85f379545a2ed9f6cceb331288842f378cf0f04012ad4ac8824aae7d6f80a00000000000000000000"

    val hex = Hex.unsafe(raw)

    val protocolUnsignedTx = deserialize[UnsignedTransaction](hex)

    val unsignedTxMissingDetails =
      TransactionService.convertProtocolUnsignedTx(protocolUnsignedTx.toOption.get).futureValue

    // No Output ref in DB, we can't find the input details
    unsignedTxMissingDetails.inputs.foreach(_.address.isEmpty is true)

    val outputEntities = unsignedTxMissingDetails.inputs.map { input =>
      outputEntityGen.sample.get.copy(
        hint = input.outputRef.hint,
        key = input.outputRef.key,
        mainChain = true
      )
    }

    // Inserting the outputs to be able to fetch the input details
    exec(OutputSchema.table ++= outputEntities)

    val unsignedTx =
      TransactionService.convertProtocolUnsignedTx(protocolUnsignedTx.toOption.get).futureValue

    // Now we have the input details
    unsignedTx.inputs.zip(outputEntities).foreach { case (input, output) =>
      input.address.get is output.address
      input.attoAlphAmount.get is output.amount
    }
  }

  "get all transactions for an address even when outputs don't contain that address" in new Fixture {

    val address0 = addressAssetProtocolGen().sample.get

    val block0 = genesisBlockEntryProtocolGen(
      timestamp = TimeStamp.unsafe(0),
      chainFrom = groupIndex,
      chainTo = groupIndex,
      addresses = address0
    ).sample.get

    val output0 = block0.transactions.head.unsigned.fixedOutputs.head

    // We create a block spending block0, but excluding the address0 from outputs
    val block1 = {
      val block = blockEntryProtocolGen(Seq(block0)).sample.get

      block.copy(
        transactions = block.transactions.map { tx =>
          tx.copy(unsigned =
            tx.unsigned.copy(
              fixedOutputs = tx.unsigned.fixedOutputs.filter(_.address != address0)
            )
          )
        }
      )
    }

    val blocks = ArraySeq(block0, block1).map(BlockFlowClient.blockProtocolToEntity)

    insertMainChainBlocks(blocks)
    val transactions =
      TransactionService
        .getTransactionsByAddress(address0, Pagination.unsafe(1, 5))
        .futureValue

    val expectedTxs = block0.transactions ++ block1.transactions.filter { tx =>
      tx.unsigned.inputs.exists(_.outputRef.key == output0.key)
    }

    transactions.map(_.hash) is expectedTxs.map(_.unsigned.txId).reverse
  }

  "get only main chain transaction for an address in case of tx in two blocks (in case of reorg)" in new Fixture {

    forAll(addressAssetProtocolGen()) { address =>
      val block0 = genesisBlockEntryProtocolGen(
        timestamp = TimeStamp.unsafe(1),
        chainFrom = groupIndex,
        chainTo = groupIndex,
        addresses = address
      ).sample.get

      // Another block with the same transactions as block0
      val block1 = blockEntryProtocolGen.sample.get.copy(transactions = block0.transactions)

      val blocks = ArraySeq(block0, block1).map(BlockFlowClient.blockProtocolToEntity)

      insertMainChainBlocks(blocks)

      // Second block is not main chain
      BlockDao.updateMainChainStatus(block1.hash, false).futureValue

      TransactionService
        .getTransactionsByAddress(address, Pagination.unsafe(1, 5))
        .futureValue
        .size is 1 // was 2 in fb7127f

      TransactionService
        .getTransaction(block0.transactions.head.unsigned.txId)
        .futureValue
        .get
        .asInstanceOf[AcceptedTransaction]
        .blockHash is block0.hash // was sometime blockHash1 in fb7127f
    }
  }

  "fall back on mempool tx" in new Fixture {
    val utx = mempooltransactionGen.sample.get

    TransactionService.getTransaction(utx.hash).futureValue is None
    MempoolDao.insertMany(ArraySeq(utx)).futureValue
    TransactionService.getTransaction(utx.hash).futureValue is Some(PendingTransaction.from(utx))
  }

  "list conflicted transactions" in new Fixture {

    val blocks        = chainGen(3, TimeStamp.zero, chainIndex).sample.get
    val blockEntities = blocks.map(BlockFlowClient.blockProtocolToEntity)

    val conflictedBlocks = chainGen(3, TimeStamp.zero, chainIndex).sample.get
    val conflictedBlockEntities =
      conflictedBlocks.map(BlockFlowClient.blockProtocolToEntity).map { block =>
        block.copy(transactions = block.transactions.map(_.copy(conflicted = Some(true))))
      }

    val allBlockEntities = blockEntities ++ conflictedBlockEntities

    BlockDao.insertAll(allBlockEntities).futureValue

    allBlockEntities.foreach { block =>
      BlockDao.updateMainChainStatus(block.hash, true).futureValue
    }

    val paginationSize = 20
    val pagination     = Pagination.unsafe(1, paginationSize)

    val expectedTxs =
      blocks
        .sortBy(-_.timestamp.millis)
        .flatMap(_.transactions)
        .take(paginationSize)
        .map(_.unsigned.txId)

    val expectedConflictedTxs =
      conflictedBlocks
        .sortBy(-_.timestamp.millis)
        .flatMap(_.transactions)
        .take(paginationSize)
        .map(_.unsigned.txId)

    TransactionService.list(pagination, None).futureValue.map(_.hash) is expectedTxs

    TransactionService
      .list(pagination, Some(TxStatusType.Conflicted))
      .futureValue
      .map(_.hash) is expectedConflictedTxs
  }

  "return mempool txs of an address" in new Fixture {
    forAll(addressGen, Gen.listOf(mempooltransactionGen)) { case (address, utxs) =>
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

  "get total number of transactions using finalized tx count cached value" in new Fixture {

    val startTime = FinalizerService.finalizationTime.minusUnsafe(Duration.ofDaysUnsafe(1))

    val finalizedBlocks =
      chainGen(3, startTime, chainIndex).sample.get.map(BlockFlowClient.blockProtocolToEntity)

    val nonFinalizedBlocks =
      chainGen(3, TimeStamp.now(), ChainIndex(GroupIndex.unsafe(1), groupIndex)).sample.get
        .map(BlockFlowClient.blockProtocolToEntity)

    val blocks = finalizedBlocks ++ nonFinalizedBlocks

    insertMainChainBlocks(blocks)

    val expectedTxsCount = blocks.map(_.transactions.size).sum

    eventually {

      TransactionService.getTotalNumber() is expectedTxsCount

      val finalizedTxCount = databaseConfig.db
        .run(AppStateQueries.get(FinalizedTxCount).map(_.map(_.count).getOrElse(0)))
        .futureValue

      // we check that the transaction cache used both the `FinaliazedTxCount` and the manual counting of non-finalized txs.
      finalizedTxCount > 0 is true
      finalizedTxCount < expectedTxsCount is true
    }
  }

  "get address total number of transactions" in new Fixture {

    val startTime = FinalizerService.finalizationTime.minusUnsafe(Duration.ofDaysUnsafe(1))

    val finalizedBlocks =
      chainGen(3, startTime, chainIndex).sample.get.map(BlockFlowClient.blockProtocolToEntity)

    val nonFinalizedBlocks =
      chainGen(3, TimeStamp.now(), ChainIndex(GroupIndex.unsafe(1), groupIndex)).sample.get
        .map(BlockFlowClient.blockProtocolToEntity)

    val blocks = finalizedBlocks ++ nonFinalizedBlocks
    val transactions =
      blockEntitiesToBlockEntries(ArraySeq(blocks)).flatMap(_.flatMap(_.transactions))

    val addresses = blocks.flatMap(_.outputs.map(_.address)).distinct

    insertMainChainBlocks(blocks)

    def testAddresses() = {
      addresses.foreach { address =>
        val apiAddress = protocolAddressToApi(address)

        val expectedTxsCount =
          transactions
            .filter(tx =>
              tx.outputs.exists(out => addressEqual(out.address, apiAddress)) || tx.inputs
                .exists(
                  _.address.map(inAddress => addressEqual(inAddress, apiAddress)).getOrElse(false)
                )
            )
            .distinctBy(_.hash)
            .size

        TransactionService
          .getTransactionsNumberByAddress(apiAddress)
          .futureValue is expectedTxsCount

      }
    }

    // Make sure the AddressTotalTransactionSchema is first empty
    databaseConfig.db.run(AddressTotalTransactionSchema.table.result).futureValue.isEmpty is true

    testAddresses()

    // Make sure the AddressTotalTransactionSchema is now populated
    databaseConfig.db.run(AddressTotalTransactionSchema.table.result).futureValue.isEmpty is false

    // With a pupulated AddressTotalTransactionSchema, the result should be the same
    testAddresses()
  }

  "preserve outputs order" in new TxsByAddressFixture {

    val outputs = blocks.flatMap(_.outputs)

    insertMainChainBlocks(blocks)

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
    // TODO Test this please
    // We need to generate a coherent blockflow, otherwise the queries can't match the inputs with outputs

  }

  "check active address" in new TxsByAddressFixture {

    val address2 = addressGen.sample.get

    BlockDao.insertAll(blocks).futureValue

    TransactionService.areAddressesActive(ArraySeq(address, address2)).futureValue is ArraySeq(
      true,
      false
    )
  }

  "get latest transactionInfo" in new TxsByAddressFixture {
    forAll(addressGen) { randomAddress =>
      TransactionService.getLatestTransactionInfoByAddress(randomAddress).futureValue is None
    }

    val expected = transactions.maxBy(_.timestamp)
    val info = TransactionInfo(
      expected.hash,
      expected.blockHash,
      expected.timestamp,
      expected.coinbase
    )

    TransactionService.getLatestTransactionInfoByAddress(address).futureValue is Some(info)

  }

  "export transactions by address" in new TxsByAddressFixture {
    forAll(Gen.choose(1, 4)) { batchSize =>
      val flowable = TransactionService
        .exportTransactionsByAddress(address, fromTs, toTs, batchSize, 8)

      val result: Seq[Buffer] =
        flowable.toList().blockingGet().asScala

      // TODO Check data format and not only the size

      result.size is ((transactions.size.toFloat / batchSize.toFloat).ceil.toInt + 1) // header

      // Checking the final csv has the correct number of lines
      val csvFile = result.map(_.toString()).mkString.split('\n')

      val tokenTxNumber =
        blocks.flatMap(_.outputs).flatMap(_.tokens.map(_.map(_.id))).flatten.distinct.size

      csvFile.length is (transactions.length + tokenTxNumber + 1)
    }
  }

  "get amount history" in new TxsByAddressFixture {
    Seq[IntervalType](IntervalType.Hourly, IntervalType.Daily).foreach { intervalType =>
      val history = TransactionService
        .getAmountHistory(address, fromTs, toTs, intervalType)
        .futureValue
        .map { case (ts, sum) => (ts.millis, sum) }

      val times = history.map(_._1)

      // Test that history is always ordered correctly
      times is times.sorted

      // TODO Test history amount value
      history.nonEmpty is true
    }
  }

  "has addres more txs than threshold" in new TxsByAddressFixture {
    TransactionService
      .hasAddressMoreTxsThan(address, fromTs, toTs, transactions.size)
      .futureValue is false
    TransactionService
      .hasAddressMoreTxsThan(address, fromTs, toTs, transactions.size - 1)
      .futureValue is true

    // Checking fromTs/toTs are taken into account
    val fromMs = fromTs.millis
    val toMs   = toTs.millis
    val diff   = (toMs - fromMs) / 2
    forAll(
      Gen.choose(fromTs.millis, fromTs.millis + diff).map(TimeStamp.unsafe),
      Gen.choose(toTs.millis - diff, toTs.millis).map(TimeStamp.unsafe)
    ) { case (from, to) =>
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

  "get unlock script" in new Fixture {

    val blocks = Gen
      .listOfN(20, blockEntityGen(chainIndex))
      .sample
      .get

    BlockDao.insertAll(blocks).futureValue

    val addresses = blocks.flatMap(_.inputs.flatMap(_.outputRefAddress)).distinct

    addresses.foreach { address =>
      TransactionService
        .getUnlockScript(address)
        .futureValue
        .isDefined is true
    }
  }

  trait Fixture {
    implicit val blockCache: BlockCache             = TestBlockCache()
    implicit val transactionCache: TransactionCache = TestTransactionCache()
    implicit val blockflowClient: BlockFlowClient   = new EmptyBlockFlowClient {}
    implicit val addressTxCountCache: AddressTxCountCache =
      TestAddressTxCountCache()

    val groupIndex = GroupIndex.Zero
    val chainIndex = ChainIndex(groupIndex, groupIndex)

    def insertMainChainBlocks(blocks: ArraySeq[BlockEntity]) = {

      val latestBlocks = blocks.groupBy(b => (b.chainFrom, b.chainTo)).map { case (_, blocks) =>
        blocks.maxBy(_.timestamp)
      }

      BlockDao.insertAll(blocks).futureValue

      foldFutures(latestBlocks)(BlockDao.updateLatestBlock).futureValue

      eventually {
        // Check that the block cache is updated
        blockCache.getAllLatestBlocks().futureValue.head._2.timestamp > TimeStamp.zero is true
      }

      foldFutures(blocks) { block =>
        for {
          _ <- databaseConfig.db.run(InputUpdateQueries.updateInputs())
          _ <- BlockDao.updateMainChainStatus(block.hash, true)
        } yield (())
      }.futureValue

      FinalizerService.finalizeOutputs().futureValue
    }
  }

  trait TxsByAddressFixture extends Fixture {

    val address = addressGen.sample.get
    val halfDay = Duration.ofHoursUnsafe(12)
    val now     = TimeStamp.now()

    val blocks = Gen
      .listOfN(5, blockEntityGen(chainIndex))
      .map(_.zipWithIndex.map { case (block, index) =>
        val timestamp = now + (halfDay.timesUnsafe(index.toLong))
        block.copy(
          timestamp = timestamp,
          outputs = block.outputs.map(
            _.copy(
              address = address,
              grouplessAddress = AddressUtil.convertToGrouplessAddress(address),
              timestamp = timestamp
            )
          ),
          inputs = block.inputs.map(
            _.copy(
              outputRefAddress = Some(address),
              outputRefGrouplessAddress = AddressUtil.convertToGrouplessAddress(address),
              timestamp = timestamp
            )
          ),
          transactions = block.transactions.map { tx =>
            tx.copy(timestamp = timestamp)
          }
        )
      })
      .sample
      .get

    insertMainChainBlocks(blocks)

    val transactions = blocks.flatMap(_.transactions).sortBy(_.timestamp)
    val timestamps   = transactions.map(_.timestamp).distinct
    val fromTs       = timestamps.head
    val toTs         = timestamps.last + Duration.ofMillisUnsafe(1)
  }
}
