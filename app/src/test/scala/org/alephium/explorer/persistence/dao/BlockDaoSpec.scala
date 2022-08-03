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
import scala.io.{Codec, Source}
import scala.util.Random

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.{model, ApiModelCodec}
import org.alephium.explorer.{AlephiumSpec, Generators, GroupSetting}
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenModel._
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Pagination}
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.InputUpdateQueries
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.util.TestUtils._
import org.alephium.json.Json._
import org.alephium.protocol.model.ChainIndex
import org.alephium.util.{Duration, TimeStamp}

class BlockDaoSpec
    extends AlephiumSpec
    with DatabaseFixtureForEach
    with DBRunner
    with ScalaFutures
    with Generators
    with Eventually {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  "updateMainChainStatus correctly" in new Fixture {
    forAll(Gen.oneOf(blockEntities), arbitrary[Boolean]) {
      case (block, mainChainInput) =>
        BlockDao.insert(block).futureValue
        BlockDao.updateMainChainStatus(block.hash, mainChainInput).futureValue

        val fetchedBlock = BlockDao.get(block.hash).futureValue.get
        fetchedBlock.hash is block.hash
        fetchedBlock.mainChain is mainChainInput

        val inputQuery =
          InputSchema.table.filter(_.blockHash === block.hash).map(_.mainChain).result
        val outputQuery =
          OutputSchema.table.filter(_.blockHash === block.hash).map(_.mainChain).result

        val inputs: Seq[Boolean]  = run(inputQuery).futureValue
        val outputs: Seq[Boolean] = run(outputQuery).futureValue

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
      val headerHash: Seq[BlockEntry.Hash] = run(blockheadersQuery).futureValue
      headerHash.size is 1
      headerHash.foreach(_.is(block.hash))
      block.transactions.nonEmpty is true

      val inputQuery  = InputSchema.table.filter(_.blockHash === block.hash).result
      val outputQuery = OutputSchema.table.filter(_.blockHash === block.hash).result
      val blockDepsQuery =
        BlockDepsSchema.table.filter(_.hash === block.hash).map(_.dep).result
      val transactionsQuery =
        TransactionSchema.table.filter(_.blockHash === block.hash).result
      val queries  = Seq(inputQuery, outputQuery, blockDepsQuery, transactionsQuery)
      val dbInputs = Seq(block.inputs, block.outputs, block.deps, block.transactions)

      def checkDuplicates[T](dbInput: Seq[T], dbOutput: Seq[T]) = {
        dbOutput.size is dbInput.size
        dbOutput.foreach(output => dbInput.contains(output) is true)
      }

      dbInputs
        .zip(queries)
        .foreach { case (dbInput, query) => checkDuplicates(dbInput, run(query).futureValue) }
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
    val from       = GroupIndex.unsafe(0)
    val to         = GroupIndex.unsafe(0)
    val chainIndex = ChainIndex.unsafe(0, 0)
    val block1 = blockHeaderGen.sample.get.copy(mainChain = true,
                                                chainFrom = from,
                                                chainTo   = to,
                                                timestamp = now)
    val block2 = blockHeaderGen.sample.get.copy(mainChain = true,
                                                chainFrom = from,
                                                chainTo   = to,
                                                timestamp = now.plusMinutesUnsafe(2))
    val block3 = blockHeaderGen.sample.get.copy(mainChain = true,
                                                chainFrom = from,
                                                chainTo   = to,
                                                timestamp = now.plusMinutesUnsafe(4))
    val block4 = blockHeaderGen.sample.get.copy(mainChain = true,
                                                chainFrom = from,
                                                chainTo   = to,
                                                timestamp = now.plusMinutesUnsafe(6))

    run(
      LatestBlockSchema.table ++=
        chainIndexes.map {
          case (from, to) =>
            LatestBlock.fromEntity(blockEntityGen(from, to, None).sample.get).copy(timestamp = now)
        }).futureValue

    run(BlockHeaderSchema.table ++= Seq(block1, block2, block3, block4)).futureValue

    BlockDao.getAverageBlockTime().futureValue.head is ((chainIndex, Duration.ofMinutesUnsafe(2)))
  }

  "cache mainChainQuery's rowCount when table is non-empty" in new Fixture {
    //generate some entities with random mainChain value
    val entitiesGenerator: Gen[List[BlockEntity]] =
      Gen
        .listOf(genBlockEntityWithOptionalParent(randomMainChainGen = Some(arbitrary[Boolean])))
        .map(_.map(_._1))

    forAll(entitiesGenerator) { blockEntities =>
      //clear existing data
      run(BlockHeaderSchema.table.delete).futureValue

      BlockDao.insertAll(blockEntities).futureValue

      //expected row count in cache
      val expectedMainChainCount = blockEntities.count(_.mainChain)

      //invoking listMainChainSQL would populate the cache with the row count
      eventually {
        BlockDao
          .listMainChain(Pagination.unsafe(0, 1))
          .futureValue
          ._2 is expectedMainChainCount
      }
    }
  }

  "refresh row count cache of mainChainQuery when new data is inserted" in new Fixture {
    //generate some entities with random mainChain value
    val entitiesGenerator: Gen[List[BlockEntity]] =
      Gen
        .listOf(genBlockEntityWithOptionalParent(randomMainChainGen = Some(arbitrary[Boolean])))
        .map(_.map(_._1))

    forAll(entitiesGenerator, entitiesGenerator) {
      case (entities1, entities2) =>
        //clear existing data
        run(BlockHeaderSchema.table.delete).futureValue

        /** INSERT BATCH 1 - [[entities1]] */
        BlockDao.insertAll(entities1).futureValue
        //expected count
        val expectedMainChainCount = entities1.count(_.mainChain)
        //Assert the query return expected count
        eventually {
          BlockDao
            .listMainChain(Pagination.unsafe(0, 1, Random.nextBoolean()))
            .futureValue
            ._2 is expectedMainChainCount
        }

        /** INSERT BATCH 2 - [[entities2]] */
        //insert the next batch of block entities
        BlockDao.insertAll(entities2).futureValue
        //Expected total row count in cache
        val expectedMainChainCountTotal = entities2.count(_.mainChain) + expectedMainChainCount
        //Dispatch a query so the cache get populated
        eventually {
          BlockDao
            .listMainChain(Pagination.unsafe(0, 1, Random.nextBoolean()))
            .futureValue
            ._2 is expectedMainChainCountTotal
        }
    }
  }

  "updateInputs" should {
    "successfully insert InputEntity" when {
      "there are no persisted outputs" in new Fixture {
        forAll(Gen.listOf(inputEntityGen())) { _ =>
          //No persisted outputs so expect inputs to persist nothing
          run(InputUpdateQueries.updateInputs()).futureValue is ()
          run(TransactionPerAddressSchema.table.result).futureValue is Seq.empty
        }
      }

      "there are existing outputs" in new Fixture {
        forAll(Gen.listOf(genInputOutput())) { inputOutputs =>
          //clear tables
          run(OutputSchema.table.delete).futureValue
          run(TransactionPerAddressSchema.table.delete).futureValue

          val outputs = inputOutputs.map(_._2)
          val inputs  = inputOutputs.map(_._1)

          //insert outputs
          run(OutputSchema.table ++= outputs).futureValue should contain(outputs.size)
          //insert inputs
          run(InputSchema.table ++= inputs).futureValue
          run(InputUpdateQueries.updateInputs()).futureValue is ()

          //expected rows in table TransactionPerAddressSchema
          val expected     = toTransactionPerAddressEntities(inputOutputs)
          val transactions = run(TransactionPerAddressSchema.table.result).futureValue
          //check rows are as expected
          transactions should contain theSameElementsAs expected
        }
      }
    }
  }

  trait Fixture extends ApiModelCodec {
    implicit val groupSettings: GroupSetting = GroupSetting(groupNum)
    implicit val blockCache: BlockCache      = BlockCache()

    val blockflow: Seq[Seq[model.BlockEntry]] =
      blockFlowGen(maxChainSize = 5, startTimestamp = TimeStamp.now()).sample.get
    val blocksProtocol: Seq[model.BlockEntry] = blockflow.flatten
    val blockEntities: Seq[BlockEntity]       = blocksProtocol.map(BlockFlowClient.blockProtocolToEntity)
  }
}
