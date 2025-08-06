// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq
import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.Random

import org.scalacheck.{Arbitrary, Gen}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model.Height
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.model.BlockHeader
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{ChainIndex, GroupIndex}

class BlockQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with TestDBRunner {

  /** Finds a block with maximum height. If two blocks have the same height, returns the one with
    * maximum timestamp.
    */
  def maxHeight(blocks: Iterable[BlockHeader]): Option[BlockHeader] =
    blocks.foldLeft(Option.empty[BlockHeader]) { case (currentMax, header) =>
      currentMax match {
        case Some(current) =>
          if (header.height > current.height) {
            Some(header)
          } else if (header.height == current.height) {
            maxTimeStamp(Array(current, header))
          } else {
            currentMax
          }

        case None =>
          Some(header)
      }
    }

  /** Finds a block with maximum timestamp */
  def maxTimeStamp(blocks: Iterable[BlockHeader]): Option[BlockHeader] =
    blocks.foldLeft(Option.empty[BlockHeader]) { case (currentMax, header) =>
      currentMax match {
        case Some(current) =>
          if (header.timestamp > current.timestamp) {
            Some(header)
          } else {
            currentMax
          }

        case None =>
          Some(header)
      }
    }

  /** Filter blocks within the given chain */
  def filterBlocksInChain(
      blocks: Iterable[BlockHeader],
      chainIndex: ChainIndex
  ): Iterable[BlockHeader] =
    blocks.filter { header =>
      header.chainFrom == chainIndex.from &&
      header.chainTo == chainIndex.to
    }

  /** Sum height of all blocks */
  def sumHeight(blocks: Iterable[BlockHeader]): Option[Height] =
    blocks.foldLeft(Option.empty[Height]) { case (currentSum, header) =>
      currentSum match {
        case Some(sum) => Some(Height.unsafe(sum.value + header.height.value))
        case None      => Some(header.height)
      }
    }

  "insert and ignore block_headers" in {

    forAll(Gen.listOf(updatedBlockHeaderGen())) { existingAndUpdates =>
      // fresh table
      exec(BlockHeaderSchema.table.delete)

      val existing = existingAndUpdates.map(_._1) // existing blocks
      val ignored  = existingAndUpdates.map(_._2) // ignored blocks

      val query = BlockQueries.insertBlockHeaders(existing)

      // insert existing
      exec(query) is existing.size
      exec(BlockHeaderSchema.table.result) should contain allElementsOf existing

      // insert should ignore existing inputs
      exec(BlockQueries.insertBlockHeaders(ignored)) is 0
      exec(BlockHeaderSchema.table.result) should contain allElementsOf existing
    }
  }

  "insert deps, transactions, inputs, outputs, block_headers" in {

    forAll(Gen.listOf(genBlockEntityWithOptionalParent().map(_._1))) { entities =>
      // clear all tables
      exec(BlockHeaderSchema.table.delete)
      exec(TransactionSchema.table.delete)
      exec(InputSchema.table.delete)
      exec(OutputSchema.table.delete)

      // execute insert on blocks and expect all tables get inserted
      exec(BlockQueries.insertBlockEntity(entities, groupSetting.groupNum)) is ()

      // check block_headers table
      val actualBlockHeaders = exec(BlockHeaderSchema.table.result)
      val expectBlockHeaders = entities.map(_.toBlockHeader(groupSetting.groupNum))
      actualBlockHeaders should contain allElementsOf expectBlockHeaders

      // check transactions table
      val actualTransactions   = exec(TransactionSchema.table.result)
      val expectedTransactions = entities.flatMap(_.transactions)
      actualTransactions should contain allElementsOf expectedTransactions

      // check inputs table
      val actualInputs   = exec(InputSchema.table.result)
      val expectedInputs = entities.flatMap(_.inputs)
      actualInputs should contain allElementsOf expectedInputs

      //// check outputs table
      // val actualOutputs   = exec(outputsTable.result)
      // val expectedOutputs = entities.flatMap(_.outputs)
      // actualOutputs should contain allElementsOf expectedOutputs

      // There is no need for testing updates here since updates are already
      // tested each table's individual test-cases.
    }
  }

  "getBlockHeaderAction" should {
    "search BlockHeader" when {
      "hash exists" in {
        forAll(blockHeaderGen) { expectedHeader =>
          // clear table
          exec(BlockHeaderSchema.table.delete)
          // insert a block_header
          exec(BlockHeaderSchema.table += expectedHeader) is 1
          // read the inserted block_header
          val actualHeader = exec(BlockQueries.getBlockHeaderAction(expectedHeader.hash))
          // read block_header should be the same as the inserted block_header
          actualHeader should contain(expectedHeader)
        }
      }

      "hash does not exist" in {
        // clear table
        exec(BlockHeaderSchema.table.delete)

        forAll(blockHashGen) { hash =>
          // table is empty
          exec(BlockHeaderSchema.table.length.result) is 0
          // expect None
          exec(BlockQueries.getBlockHeaderAction(hash)) is None
        }
      }
    }
  }

  "getHeadersAtHeightIgnoringOne" should {
    "return empty" when {
      "data is empty" in {
        val query =
          BlockQueries.getHashesAtHeightIgnoringOne(
            fromGroup = GroupIndex.Zero,
            toGroup = GroupIndex.Zero,
            height = Height.unsafe(19),
            hashToIgnore = blockHashGen.sample.get
          )

        exec(query).size is 0
      }
    }

    "fetch hashes ignoring/filter-out an existing hash" in {
      // same groupIndex and height for all blocks
      val groupIndex = groupIndexGen.sample.get
      val height     = heightGen.sample.get

      // build a blockHeader generator with constant chain and height data
      val blockGen =
        blockHeaderGenWithDefaults(
          chainFrom = Gen.const(groupIndex),
          chainTo = Gen.const(groupIndex),
          height = Gen.const(height)
        )

      forAll(Gen.nonEmptyListOf(blockGen)) { blocks =>
        exec(BlockHeaderSchema.table.delete)     // clear table
        exec(BlockHeaderSchema.table ++= blocks) // persist test data

        // randomly select a block to ignore
        val hashToIgnore = Random.shuffle(blocks).head

        val query =
          BlockQueries.getHashesAtHeightIgnoringOne(
            fromGroup = groupIndex,
            toGroup = groupIndex,
            height = height,
            hashToIgnore = hashToIgnore.hash
          )

        val actualResult   = exec(query)                                  // actual result
        val expectedResult = blocks.filter(_ != hashToIgnore).map(_.hash) // expected result

        actualResult should not contain hashToIgnore // ignored hash is not included
        actualResult should contain theSameElementsAs expectedResult
      }
    }
  }

  "getBlockChainInfo" should {
    "return none" when {
      "database is empty" in {
        val blockHash = blockHashGen.sample.get
        exec(BlockQueries.getBlockChainInfo(blockHash)) is None
      }
    }

    "return chainInfo for existing blocks or else None" in {
      forAll(Gen.nonEmptyListOf(blockHeaderGen)) { blockHeaders =>
        val blockToRemove  = Random.shuffle(blockHeaders).head       // block to remove
        val blocksToInsert = blockHeaders.filter(_ != blockToRemove) // blocks to insert

        // insert blocks
        exec(BlockQueries.insertBlockHeaders(blocksToInsert)) is blocksToInsert.size

        // returns None for non-existing block
        exec(BlockQueries.getBlockChainInfo(blockToRemove.hash)) is None

        // inserted blocks should return expected chainFrom, chainTo and mainChain information
        blocksToInsert foreach { block =>
          val (chainFrom, chainTo, mainChain) =
            exec(BlockQueries.getBlockChainInfo(block.hash)).get
          block.chainFrom is chainFrom
          block.chainTo is chainTo
          block.mainChain is mainChain
        }
      }
    }
  }

  "updateMainChainStatusesQuery" should {
    "not throw Exception" when {
      "query input is empty" in {
        exec(
          BlockQueries.updateMainChainStatuses(ArraySeq.empty, Random.nextBoolean())
        ) is 0
      }
    }

    "transactionally update all dependant tables with new mainChain value" when {
      "there are one or more blocks" in {
        forAll(
          Gen.nonEmptyListOf(blockAndItsMainChainEntitiesGen()),
          Arbitrary.arbitrary[Boolean]
        ) { case (testData, updatedMainChainValue) =>
          val entities           = testData.map(_._1)
          val txsPerToken        = testData.map(_._2)
          val tokenTxsPerAddress = testData.map(_._3)
          val tokenOutputs       = testData.map(_._4)

          // Insert BlockEntity test data
          exec(BlockQueries.insertBlockEntity(entities, groupSetting.groupNum)) is ()
          // Insert other BlockEntity's dependant test data
          exec(TransactionPerTokenSchema.table ++= txsPerToken) is
            Some(txsPerToken.size)
          exec(TokenPerAddressSchema.table ++= tokenTxsPerAddress) is
            Some(tokenTxsPerAddress.size)
          exec(TokenOutputSchema.table ++= tokenOutputs) is
            Some(tokenOutputs.size)

          // block hashes to update
          val hashes = entities.map(_.hash)

          // Run the update query being tested
          exec(BlockQueries.updateMainChainStatuses(hashes, updatedMainChainValue)) is 0

          // Check that query result is non-empty and has it's mainChain value updated
          def check(query: Query[Rep[Boolean], Boolean, Seq]) = {
            val result = exec(query.result)
            result should not be empty
            result should contain only updatedMainChainValue
          }

          // Check the query returns empty
          def checkEmpty(query: Query[Rep[Boolean], Boolean, Seq]) =
            exec(query.result).isEmpty is true

          // Run check on all tables that are expected to have their mainChain value updated
          check(TransactionSchema.table.filter(_.blockHash inSet hashes).map(_.mainChain))
          check(OutputSchema.table.filter(_.blockHash inSet hashes).map(_.mainChain))
          check(BlockHeaderSchema.table.filter(_.hash inSet hashes).map(_.mainChain))
          check(
            TransactionPerAddressSchema.table.filter(_.blockHash inSet hashes).map(_.mainChain)
          )
          check(TransactionPerTokenSchema.table.filter(_.blockHash inSet hashes).map(_.mainChain))
          check(TokenPerAddressSchema.table.filter(_.blockHash inSet hashes).map(_.mainChain))
          check(TokenOutputSchema.table.filter(_.blockHash inSet hashes).map(_.mainChain))

          val inputsQuery = InputSchema.table.filter(_.blockHash inSet hashes).map(_.mainChain)

          // Inputs are optionally generated so if it's empty expect empty query result
          if (entities.forall(_.inputs.isEmpty)) {
            checkEmpty(inputsQuery)
          } else {
            check(inputsQuery)
          }
        }
      }
    }
  }
}
