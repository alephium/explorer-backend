// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.dao

import scala.io.{Codec, Source}
import scala.util.Random

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.{model, ApiModelCodec}
import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.config.Default
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.InputUpdateQueries
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.util.AddressUtil
import org.alephium.explorer.util.TestUtils._
import org.alephium.json.Json._
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.{BlockHash, ChainIndex, GroupIndex}
import org.alephium.util.{Duration, TimeStamp}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.JavaSerializable",
    "org.wartremover.warts.Product",
    "org.wartremover.warts.Serializable"
  )
) // Wartremover is complaining, don't now why :/
class BlockDaoSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with TestDBRunner {

  "updateMainChainStatus correctly" in new Fixture {
    forAll(Gen.oneOf(blockEntities), arbitrary[Boolean]) { case (block, mainChainInput) =>
      BlockDao.insert(block).futureValue
      BlockDao.updateMainChainStatus(block.hash, mainChainInput).futureValue

      val fetchedBlock = BlockDao.get(block.hash).futureValue.get
      fetchedBlock.hash is block.hash
      fetchedBlock.mainChain is mainChainInput

      val inputQuery =
        InputSchema.table.filter(_.blockHash === block.hash).map(_.mainChain).result
      val outputQuery =
        OutputSchema.table.filter(_.blockHash === block.hash).map(_.mainChain).result

      val inputs: Seq[Boolean]  = exec(inputQuery)
      val outputs: Seq[Boolean] = exec(outputQuery)

      inputs.size is block.inputs.size
      outputs.size is block.outputs.size
      (inputs ++ outputs).foreach(isMainChain => isMainChain is mainChainInput)
    }
  }

  "not insert a block twice" in new Fixture {
    forAll(Gen.oneOf(blockEntities)) { block =>
      BlockDao.insert(block).futureValue
      BlockDao.insert(block).futureValue

      val blockheadersQuery =
        BlockHeaderSchema.table.filter(_.hash === block.hash).map(_.hash).result
      val headerHash: Seq[BlockHash] = exec(blockheadersQuery)
      headerHash.size is 1
      headerHash.foreach(_.is(block.hash))
      block.transactions.nonEmpty is true

      val inputQuery  = InputSchema.table.filter(_.blockHash === block.hash).result
      val outputQuery = OutputSchema.table.filter(_.blockHash === block.hash).result
      val transactionsQuery =
        TransactionSchema.table.filter(_.blockHash === block.hash).result
      val queries  = Seq(inputQuery, outputQuery, transactionsQuery)
      val dbInputs = Seq(block.inputs, block.outputs, block.transactions)

      def checkDuplicates[T](dbInput: Seq[T], dbOutput: Seq[T]) = {
        dbOutput.size is dbInput.size
        dbOutput.foreach(output => dbInput.contains(output) is true)
      }

      dbInputs
        .zip(queries)
        .foreach { case (dbInput, query) => checkDuplicates(dbInput, exec(query)) }
    }
  }

  "Recreate issue #162 - not throw exception when inserting a big block" in new Fixture {
    using(Source.fromResource("big_block.json")(Codec.UTF8)) { source =>
      val rawBlock   = source.getLines().mkString
      val blockEntry = read[model.BlockEntry](rawBlock)
      val block      = BlockFlowClient.blockProtocolToEntity(blockEntry)
      BlockDao.insert(block).futureValue is ()
    }
  }

  "get average block time" in new Fixture {
    val now        = TimeStamp.now()
    val from       = GroupIndex.Zero
    val to         = GroupIndex.Zero
    val chainIndex = ChainIndex(from, to)
    val block1 = blockHeaderGen.sample.get.copy(
      mainChain = true,
      chainFrom = from,
      chainTo = to,
      timestamp = now
    )
    val block2 = blockHeaderGen.sample.get.copy(
      mainChain = true,
      chainFrom = from,
      chainTo = to,
      timestamp = now.plusMinutesUnsafe(2)
    )
    val block3 = blockHeaderGen.sample.get.copy(
      mainChain = true,
      chainFrom = from,
      chainTo = to,
      timestamp = now.plusMinutesUnsafe(4)
    )
    val block4 = blockHeaderGen.sample.get.copy(
      mainChain = true,
      chainFrom = from,
      chainTo = to,
      timestamp = now.plusMinutesUnsafe(6)
    )

    exec(
      LatestBlockSchema.table ++=
        chainIndexes.map { chainIndex =>
          LatestBlock.fromEntity(blockEntityGen(chainIndex).sample.get).copy(timestamp = now)
        }
    )

    exec(BlockHeaderSchema.table ++= Seq(block1, block2, block3, block4))

    BlockDao.getAverageBlockTime().futureValue.head is ((chainIndex, Duration.ofMinutesUnsafe(2)))
  }

  "cache mainChainQuery's rowCount when table is non-empty" in new Fixture {

    BlockDao.insertAll(blockEntities).futureValue

    val highestBlocksByChain = blockEntitiesPerChain
      .map { blocks =>
        blocks.maxByOption(_.height)
      }

    highestBlocksByChain.foreach {
      case Some(block) => BlockDao.updateLatestBlock(block).futureValue
      case _           => ()
    }

    val expectedRowCount = highestBlocksByChain.map(_.map(b => b.height.value + 1).getOrElse(0)).sum

    // invoking listMainChainSQL would populate the cache with the row count
    eventually {
      val p = BlockDao
        .listMainChain(Pagination.Reversible.unsafe(1, 1))
        .futureValue
        ._2

      p is expectedRowCount
    }
  }

  "refresh row count cache of mainChainQuery when new data is inserted" in new Fixture {
    val halfBlocks = blockEntitiesPerChain.map(chain => chain.take(chain.size / 2))

    /** INSERT BATCH 1 */
    BlockDao.insertAll(halfBlocks.flatMap(identity)).futureValue
    halfBlocks.foreach { chain =>
      chain.maxByOption(_.height) match {
        case Some(block) => BlockDao.updateLatestBlock(block).futureValue
        case _           => ()
      }
    }
    // expected count
    val expectedMainChainCount =
      halfBlocks.map(chain => chain.maxByOption(_.height).map(_.height.value + 1).getOrElse(0)).sum
    // Assert the query return expected count
    eventually {
      BlockDao
        .listMainChain(Pagination.Reversible.unsafe(1, 1, Random.nextBoolean()))
        .futureValue
        ._2 is expectedMainChainCount
    }

    // /** INSERT BATCH 2 */
    val halfBlocks2 = blockEntitiesPerChain.map(chain => chain.drop(chain.size / 2))
    // insert the next batch of block entities
    BlockDao.insertAll(halfBlocks2.flatMap(identity)).futureValue
    halfBlocks2.foreach { chain =>
      chain.maxByOption(_.height) match {
        case Some(block) => BlockDao.updateLatestBlock(block).futureValue
        case _           => ()
      }
    }
    // Expected total row count in cache
    val expectedMainChainCountTotal =
      halfBlocks2.map(chain => chain.maxByOption(_.height).map(_.height.value + 1).getOrElse(0)).sum
    // Dispatch a query so the cache get populated
    eventually {
      BlockDao
        .listMainChain(Pagination.Reversible.unsafe(1, 1, Random.nextBoolean()))
        .futureValue
        ._2 is expectedMainChainCountTotal
    }
  }

  "updateInputs" should {
    "successfully insert InputEntity" when {
      "there are no persisted outputs" in new Fixture {
        forAll(Gen.listOf(inputEntityGen())) { _ =>
          // No persisted outputs so expect inputs to persist nothing
          exec(InputUpdateQueries.updateInputs()) is ()
          exec(TransactionPerAddressSchema.table.result) is Seq.empty
        }
      }

      "there are existing outputs" in new Fixture {
        forAll(Gen.listOf(genInputOutput())) { inputOutputs =>
          // clear tables
          exec(OutputSchema.table.delete)
          exec(TransactionPerAddressSchema.table.delete)

          val outputs = inputOutputs.map(_._2)
          val inputs  = inputOutputs.map(_._1)

          // insert outputs
          exec(OutputSchema.table ++= outputs) should contain(outputs.size)
          // insert inputs
          exec(InputSchema.table ++= inputs)
          exec(InputUpdateQueries.updateInputs()) is ()

          // expected rows in table TransactionPerAddressSchema
          val expected     = toTransactionPerAddressEntities(inputOutputs)
          val transactions = exec(TransactionPerAddressSchema.table.result)
          // check rows are as expected
          transactions should contain theSameElementsAs expected
        }
      }
    }
  }

  trait Fixture extends ApiModelCodec {
    implicit val groupConfig: GroupConfig = Default.groupConfig
    implicit val blockCache: BlockCache   = TestBlockCache()

    val blockflow: Seq[Seq[model.BlockEntry]] =
      blockFlowGen(maxChainSize = 5, startTimestamp = TimeStamp.now()).sample.get
    val blockEntitiesPerChain: Seq[Seq[BlockEntity]] =
      blockflow.map(_.map(BlockFlowClient.blockProtocolToEntity))
    val blockEntities: Seq[BlockEntity] = blockEntitiesPerChain.flatten

    /** Convert input-output to
      * [[org.alephium.explorer.persistence.model.TransactionPerAddressEntity]]
      */
    def toTransactionPerAddressEntity(
        input: InputEntity,
        output: OutputEntity
    ): TransactionPerAddressEntity =
      TransactionPerAddressEntity(
        hash = output.txHash,
        address = output.address,
        grouplessAddress = AddressUtil.convertToGrouplessAddress(output.address),
        blockHash = output.blockHash,
        timestamp = output.timestamp,
        txOrder = input.txOrder,
        mainChain = output.mainChain,
        coinbase = false
      )

    /** Convert multiple input-outputs to
      * [[org.alephium.explorer.persistence.model.TransactionPerAddressEntity]]
      */
    def toTransactionPerAddressEntities(
        inputOutputs: Iterable[(InputEntity, OutputEntity)]
    ): Iterable[TransactionPerAddressEntity] =
      inputOutputs map { case (input, output) =>
        toTransactionPerAddressEntity(input, output)
      }

  }
}
