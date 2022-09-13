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

package org.alephium.explorer.persistence.queries

import scala.concurrent.ExecutionContext
import scala.math.Ordering.Implicits.infixOrderingOps

import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Minutes, Span}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumSpec, GroupSetting}
import org.alephium.explorer.GenApiModel.blockEntryHashGen
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.model.{GroupIndex, Height}
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model.BlockHeader
import org.alephium.explorer.persistence.schema._
import org.alephium.util.AVector

class BlockQueriesSpec
    extends AlephiumSpec
    with DatabaseFixtureForEach
    with DBRunner
    with ScalaFutures {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1000, Minutes))

  /**
    * Finds a block with maximum height. If two blocks have the same
    * height, returns the one with maximum timestamp.
    * */
  def maxHeight(blocks: AVector[BlockHeader]): Option[BlockHeader] =
    blocks.foldLeft(Option.empty[BlockHeader]) {
      case (currentMax, header) =>
        currentMax match {
          case Some(current) =>
            if (header.height > current.height) {
              Some(header)
            } else if (header.height == current.height) {
              maxTimeStamp(AVector(current, header))
            } else {
              currentMax
            }

          case None =>
            Some(header)
        }
    }

  /** Finds a block with maximum timestamp */
  def maxTimeStamp(blocks: AVector[BlockHeader]): Option[BlockHeader] =
    blocks.foldLeft(Option.empty[BlockHeader]) {
      case (currentMax, header) =>
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
  def filterBlocksInChain(blocks: AVector[BlockHeader],
                          chainFrom: GroupIndex,
                          chainTo: GroupIndex): AVector[BlockHeader] =
    blocks.filter { header =>
      header.chainFrom == chainFrom &&
      header.chainTo == chainTo
    }

  /** Sum height of all blocks */
  def sumHeight(blocks: AVector[BlockHeader]): Option[Height] =
    blocks.foldLeft(Option.empty[Height]) {
      case (currentSum, header) =>
        currentSum match {
          case Some(sum) => Some(Height.unsafe(sum.value + header.height.value))
          case None      => Some(header.height)
        }
    }

  "insert and ignore block_headers" in {

    forAll(Gen.listOf(updatedBlockHeaderGen())) { existingAndUpdates =>
      //fresh table
      run(BlockHeaderSchema.table.delete).futureValue

      val existing = existingAndUpdates.map(_._1) //existing blocks
      val ignored  = existingAndUpdates.map(_._2) //ignored blocks

      val query = BlockQueries.insertBlockHeaders(existing)

      //insert existing
      run(query).futureValue is existing.size
      run(BlockHeaderSchema.table.result).futureValue containsAllElementsOf existing

      //insert should ignore existing inputs
      run(BlockQueries.insertBlockHeaders(ignored)).futureValue is 0
      run(BlockHeaderSchema.table.result).futureValue containsAllElementsOf existing
    }
  }

  "insert deps, transactions, inputs, outputs, block_headers" in {

    implicit val groupSetting: GroupSetting = groupSettingGen.sample.get

    forAll(Gen.listOf(genBlockEntityWithOptionalParent().map(_._1))) { entities =>
      //clear all tables
      run(BlockHeaderSchema.table.delete).futureValue
      run(TransactionSchema.table.delete).futureValue
      run(InputSchema.table.delete).futureValue
      run(OutputSchema.table.delete).futureValue
      run(BlockDepsSchema.table.delete).futureValue

      //execute insert on blocks and expect all tables get inserted
      entities.foreach { entity =>
        run(BlockQueries.insertBlockEntity(entity, groupSetting.groupNum)).futureValue is ()
      }

      //check block_headers table
      val actualBlockHeaders = run(BlockHeaderSchema.table.result).futureValue
      val expectBlockHeaders = entities.map(_.toBlockHeader(groupSetting.groupNum))
      actualBlockHeaders containsAllElementsOf expectBlockHeaders

      //check transactions table
      val actualTransactions   = run(TransactionSchema.table.result).futureValue
      val expectedTransactions = entities.flatMap(_.transactions)
      actualTransactions containsAllElementsOf expectedTransactions

      //check inputs table
      val actualInputs   = run(InputSchema.table.result).futureValue
      val expectedInputs = entities.flatMap(_.inputs)
      actualInputs containsAllElementsOf expectedInputs

      ////check outputs table
      //val actualOutputs   = run(outputsTable.result).futureValue
      //val expectedOutputs = entities.flatMap(_.outputs)
      //actualOutputs containsAllElementsOf expectedOutputs

      //check block_deps table
      val actualDeps        = run(BlockDepsSchema.table.result).futureValue
      val expectedBlockDeps = entities.flatMap(_.toBlockDepEntities())
      actualDeps containsAllElementsOf expectedBlockDeps

      //There is no need for testing updates here since updates are already
      //tested each table's individual test-cases.
    }
  }

  "getBlockHeaderAction" should {
    "search BlockHeader" when {
      "hash exists" in {
        forAll(blockHeaderGen) { expectedHeader =>
          //clear table
          run(BlockHeaderSchema.table.delete).futureValue
          //insert a block_header
          run(BlockHeaderSchema.table += expectedHeader).futureValue is 1
          //read the inserted block_header
          val actualHeader = run(BlockQueries.getBlockHeaderAction(expectedHeader.hash)).futureValue
          //read block_header should be the same as the inserted block_header
          actualHeader should contain(expectedHeader)
        }
      }

      "hash does not exist" in {
        //clear table
        run(BlockHeaderSchema.table.delete).futureValue

        forAll(blockEntryHashGen) { hash =>
          //table is empty
          run(BlockHeaderSchema.table.length.result).futureValue is 0
          //expect None
          run(BlockQueries.getBlockHeaderAction(hash)).futureValue is None
        }
      }
    }
  }

  "noOfBlocksAndMaxBlockTimestamp" should {
    "return None" when {
      "there is no data" in {
        forAll(groupSettingGen) { implicit groupSetting =>
          run(BlockQueries.numOfBlocksAndMaxBlockTimestamp()).futureValue is None
        }
      }

      "chains being queries do not exists in database" in {
        forAll(Gen.listOf(blockHeaderGen), groupSettingGen) {
          case (blocks, groupSetting) =>
            //fetch the maximum chain-index from the groupSetting
            val maxChainIndex = groupSetting.chainIndexes.map(_.to.value).max

            //set the block's chainFrom to maxChainIndex + 1 so no blocks match the groupSetting
            val updatedBlocks = blocks.map { block =>
              block.copy(chainFrom = GroupIndex.unsafe(maxChainIndex + 1),
                         chainTo   = GroupIndex.unsafe(maxChainIndex + 10))
            }

            run(BlockHeaderSchema.table.delete).futureValue //clear table
            run(BlockHeaderSchema.table ++= updatedBlocks).futureValue //persist test data

            implicit val implicitGroupSetting: GroupSetting = groupSetting
            //No blocks exists for the groupSetting so result is None
            run(BlockQueries.numOfBlocksAndMaxBlockTimestamp()).futureValue is None

        }
      }
    }

    "return total number of blocks and max timeStamp" in {
      forAll(Gen.listOf(blockHeaderGen)) { blocks =>
        run(BlockHeaderSchema.table.delete).futureValue //clear table
        run(BlockHeaderSchema.table ++= blocks).futureValue //persist test data

        //To cover more cases, run multiple tests for different groupsSettings on the same persisted blocks.
        forAll(groupSettingGen) { implicit groupSetting =>
          //All blocks with maximum height
          val maxHeightBlocks =
            groupSetting.groupIndexes
              .flatMap {
                case (from, to) =>
                  val blocksInThisChain = filterBlocksInChain(blocks, from, to)
                  maxHeight(blocksInThisChain)
              }

          //expected total number of blocks
          val expectedNumberOfBlocks =
            sumHeight(maxHeightBlocks)

          //expected maximum timestamp
          val expectedMaxTimeStamp =
            maxTimeStamp(maxHeightBlocks).map(_.timestamp)

          //Queried result Option[(TimeStamp, Height)]
          val actualTimeStampAndNumberOfBlocks =
            run(BlockQueries.numOfBlocksAndMaxBlockTimestamp()).futureValue

          actualTimeStampAndNumberOfBlocks.map(_._1) is expectedMaxTimeStamp
          actualTimeStampAndNumberOfBlocks.map(_._2) is expectedNumberOfBlocks
        }
      }
    }
  }

  "maxHeight" should {
    "return empty List" when {
      "input is empty" in {
        run(BlockQueries.maxHeight(AVector.empty)).futureValue is AVector.empty[Option[Height]]
      }
    }

    "return non-empty List with None values" when {
      "there is no data" in {
        forAll(groupSettingGen) { implicit groupSetting =>
          val expected: AVector[Option[Height]] = AVector.fill(groupSetting.groupIndexes.size)(None)
          run(BlockQueries.maxHeight(groupSetting.groupIndexes)).futureValue is expected
        }
      }
    }

    "return non-empty list with Some values" when {
      "there is data" in {
        //short range GroupIndex so there are enough valid groups for this test
        val genGroupIndex = Gen.choose(1, 5).map(GroupIndex.unsafe)

        forAll(Gen.listOf(blockHeaderGenWithDefaults(genGroupIndex, genGroupIndex))) { blocks =>
          run(BlockHeaderSchema.table.delete).futureValue //clear table
          run(BlockHeaderSchema.table ++= blocks).futureValue //persist test data

          forAll(groupSettingGen) { implicit groupSetting =>
            //All blocks with maximum height
            val expectedHeights =
              groupSetting.groupIndexes
                .map {
                  case (from, to) =>
                    val blocksInThisChain = filterBlocksInChain(blocks, from, to)
                    maxHeight(blocksInThisChain).map(_.height)
                }

            val actualHeights =
              run(BlockQueries.maxHeight(groupSetting.groupIndexes)).futureValue

            actualHeights is expectedHeights
          }
        }
      }
    }
  }
}
