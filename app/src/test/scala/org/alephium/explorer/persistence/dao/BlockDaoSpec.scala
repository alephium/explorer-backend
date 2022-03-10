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

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.{model, ApiModelCodec}
import org.alephium.explorer.{AlephiumSpec, Generators}
import org.alephium.explorer.api.model.{BlockEntry, Pagination}
import org.alephium.explorer.persistence.{DatabaseFixture, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.InputSchema._
import org.alephium.explorer.persistence.schema.OutputSchema._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.util.TestUtils._
import org.alephium.json.Json._
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

        val inputQuery  = inputsTable.filter(_.blockHash === block.hash).map(_.mainChain).result
        val outputQuery = outputsTable.filter(_.blockHash === block.hash).map(_.mainChain).result

        val inputs: Seq[Boolean]  = run(inputQuery).futureValue
        val outputs: Seq[Boolean] = run(outputQuery).futureValue

        inputs.size is block.inputs.size
        outputs.size is block.outputs.size
        (inputs ++ outputs).foreach(isMainChain => isMainChain is mainChainInput)
    }
  }

  it should "not insert a block twice" in new Fixture {
    forAll(Gen.oneOf(blockEntities)) { block =>
      blockDao.insert(block).futureValue
      blockDao.insert(block).futureValue

      val blockheadersQuery                = blockHeadersTable.filter(_.hash === block.hash).map(_.hash).result
      val headerHash: Seq[BlockEntry.Hash] = run(blockheadersQuery).futureValue
      headerHash.size is 1
      headerHash.foreach(_.is(block.hash))
      block.transactions.nonEmpty is true

      val inputQuery        = inputsTable.filter(_.blockHash === block.hash).result
      val outputQuery       = outputsTable.filter(_.blockHash === block.hash).result
      val blockDepsQuery    = blockDepsTable.filter(_.hash === block.hash).map(_.dep).result
      val transactionsQuery = transactionsTable.filter(_.blockHash === block.hash).result
      val queries           = Seq(inputQuery, outputQuery, blockDepsQuery, transactionsQuery)
      val dbInputs          = Seq(block.inputs, block.outputs, block.deps, block.transactions)

      def checkDuplicates[T](dbInput: Seq[T], dbOutput: Seq[T]) = {
        dbOutput.size is dbInput.size
        dbOutput.foreach(output => dbInput.contains(output) is true)
      }

      dbInputs
        .zip(queries)
        .foreach { case (dbInput, query) => checkDuplicates(dbInput, run(query).futureValue) }
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
        run(blockHeadersTable.delete).futureValue
        run(transactionsTable.delete).futureValue

        //create test data
        run(blockHeadersTable ++= blocks.map(_._1)).futureValue
        run(transactionsTable ++= blocks.flatten(_._2)).futureValue

        //Assert results returned by typed and SQL query are the same
        def runAssert(page: Pagination) = {
          val sqlResult   = blockDao.listMainChainSQL(page).futureValue
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

  trait Fixture
      extends BlockHeaderSchema
      with BlockDepsSchema
      with TransactionSchema
      with DatabaseFixture
      with DBRunner
      with ApiModelCodec {
    val blockflowFetchMaxAge: Duration = Duration.ofMinutesUnsafe(30)

    val blockDao = BlockDao(groupNum, databaseConfig)
    val blockflow: Seq[Seq[model.BlockEntry]] =
      blockFlowGen(maxChainSize = 5, startTimestamp = TimeStamp.now()).sample.get
    val blocksProtocol: Seq[model.BlockEntry] = blockflow.flatten
    val blockEntities: Seq[BlockEntity]       = blocksProtocol.map(BlockFlowClient.blockProtocolToEntity)
  }
}
