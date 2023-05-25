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

import scala.collection.immutable.ArraySeq
import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.Random

import org.scalacheck.{Arbitrary, Gen}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumFutureSpec, GroupSetting}
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.model.{GroupIndex, Height}
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model.BlockHeader
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._

class BlockQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with DBRunner {

  /**
    * Finds a block with maximum height. If two blocks have the same
    * height, returns the one with maximum timestamp.
    * */
  def maxHeight(blocks: Iterable[BlockHeader]): Option[BlockHeader] =
    blocks.foldLeft(Option.empty[BlockHeader]) {
      case (currentMax, header) =>
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
  def filterBlocksInChain(blocks: Iterable[BlockHeader],
                          chainFrom: GroupIndex,
                          chainTo: GroupIndex): Iterable[BlockHeader] =
    blocks.filter { header =>
      header.chainFrom == chainFrom &&
      header.chainTo == chainTo
    }

  /** Sum height of all blocks */
  def sumHeight(blocks: Iterable[BlockHeader]): Option[Height] =
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
      run(BlockHeaderSchema.table.result).futureValue should contain allElementsOf existing

      //insert should ignore existing inputs
      run(BlockQueries.insertBlockHeaders(ignored)).futureValue is 0
      run(BlockHeaderSchema.table.result).futureValue should contain allElementsOf existing
    }
  }

  "insert deps, transactions, inputs, outputs, block_headers" in {

    forAll(Gen.listOf(genBlockEntityWithOptionalParent().map(_._1))) { entities =>
      //clear all tables
      run(BlockHeaderSchema.table.delete).futureValue
      run(TransactionSchema.table.delete).futureValue
      run(InputSchema.table.delete).futureValue
      run(OutputSchema.table.delete).futureValue
      run(BlockDepsSchema.table.delete).futureValue

      //execute insert on blocks and expect all tables get inserted
      run(BlockQueries.insertBlockEntity(entities, groupSetting.groupNum)).futureValue is ()

      //check block_headers table
      val actualBlockHeaders = run(BlockHeaderSchema.table.result).futureValue
      val expectBlockHeaders = entities.map(_.toBlockHeader(groupSetting.groupNum))
      actualBlockHeaders should contain allElementsOf expectBlockHeaders

      //check transactions table
      val actualTransactions   = run(TransactionSchema.table.result).futureValue
      val expectedTransactions = entities.flatMap(_.transactions)
      actualTransactions should contain allElementsOf expectedTransactions

      //check inputs table
      val actualInputs   = run(InputSchema.table.result).futureValue
      val expectedInputs = entities.flatMap(_.inputs)
      actualInputs should contain allElementsOf expectedInputs

      ////check outputs table
      //val actualOutputs   = run(outputsTable.result).futureValue
      //val expectedOutputs = entities.flatMap(_.outputs)
      //actualOutputs should contain allElementsOf expectedOutputs

      //check block_deps table
      val actualDeps        = run(BlockDepsSchema.table.result).futureValue
      val expectedBlockDeps = entities.flatMap(_.toBlockDepEntities())
      actualDeps should contain allElementsOf expectedBlockDeps

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

        forAll(blockHashGen) { hash =>
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
        run(BlockQueries.maxHeight(Iterable.empty)).futureValue is ArraySeq.empty
      }
    }

    "return non-empty List with None values" when {
      "there is no data" in {
        forAll(groupSettingGen) { implicit groupSetting =>
          val expected = ArraySeq.fill(groupSetting.groupIndexes.size)(None)
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

  "getHeadersAtHeightIgnoringOne" should {
    "return empty" when {
      "data is empty" in {
        val query =
          BlockQueries.getHashesAtHeightIgnoringOne(fromGroup    = GroupIndex.unsafe(0),
                                                    toGroup      = GroupIndex.unsafe(0),
                                                    height       = Height.unsafe(19),
                                                    hashToIgnore = blockHashGen.sample.get)

        run(query).futureValue.size is 0
      }
    }

    "fetch hashes ignoring/filter-out an existing hash" in {
      //same groupIndex and height for all blocks
      val groupIndex = groupIndexGen.sample.get
      val height     = heightGen.sample.get

      //build a blockHeader generator with constant chain and height data
      val blockGen =
        blockHeaderGenWithDefaults(
          chainFrom = Gen.const(groupIndex),
          chainTo   = Gen.const(groupIndex),
          height    = Gen.const(height)
        )

      forAll(Gen.nonEmptyListOf(blockGen)) { blocks =>
        run(BlockHeaderSchema.table.delete).futureValue //clear table
        run(BlockHeaderSchema.table ++= blocks).futureValue //persist test data

        //randomly select a block to ignore
        val hashToIgnore = Random.shuffle(blocks).head

        val query =
          BlockQueries.getHashesAtHeightIgnoringOne(fromGroup    = groupIndex,
                                                    toGroup      = groupIndex,
                                                    height       = height,
                                                    hashToIgnore = hashToIgnore.hash)

        val actualResult   = run(query).futureValue //actual result
        val expectedResult = blocks.filter(_ != hashToIgnore).map(_.hash) //expected result

        actualResult should not contain hashToIgnore //ignored hash is not included
        actualResult should contain theSameElementsAs expectedResult
      }
    }
  }

  "getBlockChainInfo" should {
    "return none" when {
      "database is empty" in {
        val blockHash = blockHashGen.sample.get
        run(BlockQueries.getBlockChainInfo(blockHash)).futureValue is None
      }
    }

    "return chainInfo for existing blocks or else None" in {
      forAll(Gen.nonEmptyListOf(blockHeaderGen)) { blockHeaders =>
        val blockToRemove  = Random.shuffle(blockHeaders).head //block to remove
        val blocksToInsert = blockHeaders.filter(_ != blockToRemove) //blocks to insert

        //insert blocks
        run(BlockQueries.insertBlockHeaders(blocksToInsert)).futureValue is blocksToInsert.size

        //returns None for non-existing block
        run(BlockQueries.getBlockChainInfo(blockToRemove.hash)).futureValue is None

        //inserted blocks should return expected chainFrom, chainTo and mainChain information
        blocksToInsert foreach { block =>
          val (chainFrom, chainTo, mainChain) =
            run(BlockQueries.getBlockChainInfo(block.hash)).futureValue.get
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
        run(BlockQueries.updateMainChainStatuses(ArraySeq.empty, Random.nextBoolean())).futureValue is 0
      }
    }

    "transactionally update all dependant tables with new mainChain value" when {
      "there are one or more blocks" in {
        forAll(Gen.nonEmptyListOf(blockAndItsMainChainEntitiesGen()), Arbitrary.arbitrary[Boolean]) {
          case (testData, updatedMainChainValue) =>
            val entities           = testData.map(_._1)
            val txsPerToken        = testData.map(_._2)
            val tokenTxsPerAddress = testData.map(_._3)
            val tokenOutputs       = testData.map(_._4)

            //Insert BlockEntity test data
            run(BlockQueries.insertBlockEntity(entities, groupSetting.groupNum)).futureValue is ()
            //Insert other BlockEntity's dependant test data
            run(TransactionPerTokenSchema.table ++= txsPerToken).futureValue is
              Some(txsPerToken.size)
            run(TokenPerAddressSchema.table ++= tokenTxsPerAddress).futureValue is
              Some(tokenTxsPerAddress.size)
            run(TokenOutputSchema.table ++= tokenOutputs).futureValue is
              Some(tokenOutputs.size)

            //block hashes to update
            val hashes = entities.map(_.hash)

            //Run the update query being tested
            run(BlockQueries.updateMainChainStatuses(hashes, updatedMainChainValue)).futureValue is 0

            //Check that query result is non-empty and has it's mainChain value updated
            def check(query: Query[Rep[Boolean], Boolean, Seq]) = {
              val result = run(query.result).futureValue
              result should not be empty
              result should contain only updatedMainChainValue
            }

            //Check the query returns empty
            def checkEmpty(query: Query[Rep[Boolean], Boolean, Seq]) =
              run(query.result).futureValue.isEmpty is true

            //Run check on all tables that are expected to have their mainChain value updated
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

            //Inputs are optionally generated so if it's empty expect empty query result
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
