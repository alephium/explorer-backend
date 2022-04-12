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
import scala.io.Source
import scala.util.Random

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.{model, ApiModelCodec}
import org.alephium.explorer.{AlephiumSpec, Generators}
import org.alephium.explorer.api.model.{BlockEntry, BlockEntryLite, GroupIndex, Pagination}
import org.alephium.explorer.persistence.{DatabaseFixture, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.util.TestUtils._
import org.alephium.json.Json._
import org.alephium.protocol.model.ChainIndex
import org.alephium.util.{Duration, TimeStamp}

class BlockDaoSpec extends AlephiumSpec with ScalaFutures with Generators with Eventually {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  it should "updateMainChainStatus correctly" in new Fixture {
    forAll(Gen.oneOf(blockEntities), arbitrary[Boolean]) {
      case (block, mainChainInput) =>
        blockDao.insert(block).futureValue
        blockDao.updateMainChainStatus(block.hash, mainChainInput).futureValue

        val fetchedBlock = blockDao.get(block.hash).futureValue.get
        fetchedBlock.hash is block.hash
        fetchedBlock.mainChain is mainChainInput

        val inputQuery =
          InputSchema.table.filter(_.blockHash === block.hash).map(_.mainChain).result
        val outputQuery =
          OutputSchema.table.filter(_.blockHash === block.hash).map(_.mainChain).result

        val inputs: Seq[Boolean]  = runAction(inputQuery).futureValue
        val outputs: Seq[Boolean] = runAction(outputQuery).futureValue

        inputs.size is block.inputs.size
        outputs.size is block.outputs.size
        (inputs ++ outputs).foreach(isMainChain => isMainChain is mainChainInput)
    }
  }

  it should "not insert a block twice" in new Fixture {
    forAll(Gen.oneOf(blockEntities)) { block =>
      blockDao.insert(block).futureValue
      blockDao.insert(block).futureValue

      val blockheadersQuery =
        BlockHeaderSchema.table.filter(_.hash === block.hash).map(_.hash).result
      val headerHash: Seq[BlockEntry.Hash] = runAction(blockheadersQuery).futureValue
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
        .foreach { case (dbInput, query) => checkDuplicates(dbInput, runAction(query).futureValue) }
    }
  }

  it should "list block headers via SQL and typed should return same result" in new Fixture {
    val blocksCount = 30 //number of blocks to create

    forAll(Gen.listOfN(blocksCount, blockHeaderTransactionEntityGen),
           Gen.choose(0, blocksCount),
           Gen.choose(0, blocksCount),
           arbitrary[Boolean]) {
      case (blocks, pageNum, pageLimit, reverse) =>
        //clear test data
        runAction(BlockHeaderSchema.table.delete).futureValue
        runAction(TransactionSchema.table.delete).futureValue

        //create test data
        runAction(BlockHeaderSchema.table ++= blocks.map(_._1)).futureValue
        runAction(TransactionSchema.table ++= blocks.flatten(_._2)).futureValue

        //Assert results returned by typed and SQL query are the same
        def runAssert(page: Pagination) = {
          blockDao.invalidateCacheRowCount()
          val sqlResult   = blockDao.listMainChainSQLCached(page).futureValue
          val typedResult = blockDao.listMainChain(page).futureValue
          sqlResult is typedResult
        }

        runAssert(Pagination.unsafe(0, pageLimit, reverse)) //First page test
        runAssert(Pagination.unsafe(pageNum, pageLimit, reverse)) //Random page test
        runAssert(Pagination.unsafe(blocks.size, pageLimit, reverse)) //Last page test
    }
  }

  it should "Recreate issue #162 - not throw exception when inserting a big block" in new Fixture {
    using(Source.fromResource("big_block.json")) { source =>
      val rawBlock   = source.getLines().mkString
      val blockEntry = read[model.BlockEntry](rawBlock)
      val block      = BlockFlowClient.blockProtocolToEntity(blockEntry)
      blockDao.insert(block).futureValue is ()
    }
  }

  it should "get average block time" in new Fixture {
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

    runAction(
      LatestBlockSchema.table ++=
        chainIndexes.map {
          case (from, to) =>
            LatestBlock.fromEntity(blockEntityGen(from, to, None).sample.get).copy(timestamp = now)
        }).futureValue

    runAction(BlockHeaderSchema.table ++= Seq(block1, block2, block3, block4)).futureValue

    blockDao.getAverageBlockTime().futureValue.head is ((chainIndex, Duration.ofMinutesUnsafe(2)))
  }

  it should "cache mainChainQuery's rowCount when table is empty" in new Fixture {
    //Initially the cache is unpopulated so it should return None
    blockDao.getRowCountFromCacheIfPresent(blockDao.mainChainQuery) is None

    //dispatch listMainChainSQL on an empty table and expect the cache to be populated with 0 count
    forAll(Gen.posNum[Int], Gen.posNum[Int], arbitrary[Boolean]) {
      case (page, limit, reverse) =>
        blockDao
          .listMainChainSQLCached(Pagination.unsafe(page, limit min 1, reverse))
          .futureValue is ((Seq.empty[BlockEntryLite], 0))

        //assert the cache is populated
        blockDao
          .getRowCountFromCacheIfPresent(blockDao.mainChainQuery)
          .map(_.futureValue) is Some(0)
    }
  }

  it should "cache mainChainQuery's rowCount when table is non-empty" in new Fixture {
    //generate some entities with random mainChain value
    val entitiesGenerator: Gen[List[BlockEntity]] =
      Gen
        .listOf(genBlockEntityWithOptionalParent(randomMainChainGen = Some(arbitrary[Boolean])))
        .map(_.map(_._1))

    forAll(entitiesGenerator) { blockEntities =>
      //clear existing data
      runAction(BlockHeaderSchema.table.delete).futureValue

      //insert new blockEntities and expect the cache to get invalided
      blockDao.insertAll(blockEntities).futureValue
      //Assert the cache is invalidated after insert
      blockDao.getRowCountFromCacheIfPresent(blockDao.mainChainQuery) is None

      //expected row count in cache
      val expectedMainChainCount = blockEntities.count(_.mainChain)

      //invoking listMainChainSQL would populate the cache with the row count
      blockDao
        .listMainChainSQLCached(Pagination.unsafe(0, 1))
        .futureValue
        ._2 is expectedMainChainCount

      //check the cache directly and it should contain the row count
      blockDao
        .getRowCountFromCacheIfPresent(blockDao.mainChainQuery)
        .map(_.futureValue) is Some(expectedMainChainCount)
    }
  }

  it should "refresh row count cache of mainChainQuery when new data is inserted" in new Fixture {
    //generate some entities with random mainChain value
    val entitiesGenerator: Gen[List[BlockEntity]] =
      Gen
        .listOf(genBlockEntityWithOptionalParent(randomMainChainGen = Some(arbitrary[Boolean])))
        .map(_.map(_._1))

    def expectCacheIsInvalidated() =
      blockDao.getRowCountFromCacheIfPresent(blockDao.mainChainQuery) is None

    forAll(entitiesGenerator, entitiesGenerator) {
      case (entities1, entities2) =>
        //clear existing data
        runAction(BlockHeaderSchema.table.delete).futureValue

        /** INSERT BATCH 1 - [[entities1]] */
        blockDao.insertAll(entities1).futureValue
        expectCacheIsInvalidated()
        //expected count
        val expectedMainChainCount = entities1.count(_.mainChain)
        //Assert the query return expected count
        blockDao
          .listMainChainSQLCached(Pagination.unsafe(0, 1, Random.nextBoolean()))
          .futureValue
          ._2 is expectedMainChainCount
        //Assert the cache contains the right row count
        blockDao
          .getRowCountFromCacheIfPresent(blockDao.mainChainQuery)
          .map(_.futureValue) is Some(expectedMainChainCount)

        /** INSERT BATCH 2 - [[entities2]] */
        //insert the next batch of block entities
        blockDao.insertAll(entities2).futureValue
        expectCacheIsInvalidated()
        //Expected total row count in cache
        val expectedMainChainCountTotal = entities2.count(_.mainChain) + expectedMainChainCount
        //Dispatch a query so the cache get populated
        blockDao
          .listMainChainSQLCached(Pagination.unsafe(0, 1, Random.nextBoolean()))
          .futureValue
          ._2 is expectedMainChainCountTotal
        //Dispatch a query so the cache get populated
        blockDao
          .getRowCountFromCacheIfPresent(blockDao.mainChainQuery)
          .map(_.futureValue) is Some(expectedMainChainCountTotal)
    }
  }

  trait Fixture extends DatabaseFixture with DBRunner with ApiModelCodec {
    val blockflowFetchMaxAge: Duration = Duration.ofMinutesUnsafe(30)

    val blockDao = new BlockDao.Impl(groupNum, databaseConfig)
    val blockflow: Seq[Seq[model.BlockEntry]] =
      blockFlowGen(maxChainSize = 5, startTimestamp = TimeStamp.now()).sample.get
    val blocksProtocol: Seq[model.BlockEntry] = blockflow.flatten
    val blockEntities: Seq[BlockEntity]       = blocksProtocol.map(BlockFlowClient.blockProtocolToEntity)
  }
}
