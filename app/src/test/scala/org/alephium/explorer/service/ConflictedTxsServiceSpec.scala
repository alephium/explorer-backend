// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

//scalastyle:off file.size.limit
package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.cache._
import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{BlockHash, ChainIndex, GroupIndex, TransactionId}
import org.alephium.util.TimeStamp

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
    "org.wartremover.warts.DefaultArguments",
    "org.wartremover.warts.AsInstanceOf"
  )
)
class ConflictedTxsServiceSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with TestDBRunner {

  "ConflictedTxsService " should {
    "not trigger anything " when {
      "no blocks were inserted" in new Fixture {
        val input1 = mainChainInput()
        val input2 = mainChainInput(input1.outputRefKey)

        insertInputs(input1, input2)

        implicit val blockFlowClient: BlockFlowClient = new EmptyBlockFlowClient {}

        ConflictedTxsService
          .handleConflictedTxs(ArraySeq.empty)
          .futureValue

        assertNullConflictedInputs()

      }

      "no intra-blocks only" in new Fixture {
        val input1 = mainChainInput()
        val input2 = mainChainInput(input1.outputRefKey)

        insertInputs(input1, input2)

        val nonIntraBlocks =
          ArraySeq.from(Gen.nonEmptyListOf(blockGen).sample.get.filterNot(_.isIntraGroup()))

        implicit val blockFlowClient: BlockFlowClient = new EmptyBlockFlowClient {}

        ConflictedTxsService
          .handleConflictedTxs(nonIntraBlocks)
          .futureValue

        assertNullConflictedInputs()
      }
    }

    "find and update the conflicts" when {
      "2 main chain input share same output ref key " in new Fixture {
        val (blocks, inputs) = setupMainChainBlocks(2)

        implicit val blockFlowClient: BlockFlowClient =
          makeBlockFlowClient(
            blocks,
            conflictedBlockHash = Some(blocks(0).hash),
            conflictedTxs = Some(ArraySeq(inputs(0).txHash))
          )

        ConflictedTxsService
          .handleConflictedTxs(ArraySeq(intraBlock))
          .futureValue

        assertConflictStates(
          Seq(
            (blocks(0).hash, true, Some(ArraySeq(inputs(0).txHash))),
            (blocks(1).hash, false, None)
          )
        )
      }

      "3 main chain input share same output ref key " in new Fixture {
        val (blocks, inputs) = setupMainChainBlocks(3)

        implicit val blockFlowClient: BlockFlowClient =
          makeBlockFlowClient(
            blocks,
            conflictedBlockHash = Some(blocks(0).hash),
            conflictedTxs = Some(ArraySeq(inputs(0).txHash))
          )

        ConflictedTxsService
          .handleConflictedTxs(ArraySeq(intraBlock))
          .futureValue

        assertConflictStates(
          Seq(
            (blocks(0).hash, true, Some(ArraySeq(inputs(0).txHash))),
            (blocks(1).hash, false, None),
            (blocks(2).hash, false, None)
          )
        )
      }

      "a reorg happen, with one block getting out of main_chain" in new Fixture {
        val (blocks, inputs) = setupMainChainBlocks(2)

        var nodeBlocks = blocks

        implicit val blockFlowClient: BlockFlowClient = new EmptyBlockFlowClient {
          override def fetchBlock(fromGroup: GroupIndex, hash: BlockHash): Future[BlockEntity] = {
            nodeBlocks.find(_.hash == hash) match {
              case Some(block) =>
                Future.successful(block)
              case None => Future.failed(new Exception(s"Block not found: $hash"))
            }
          }
        }

        // block1 has conflicted txs, block2 not
        nodeBlocks = blocks.map { block =>
          if (block.hash == blocks(0).hash) {
            block.copy(conflictedTxs = Some(ArraySeq(inputs(0).txHash)))
          } else {
            block
          }
        }

        ConflictedTxsService
          .handleConflictedTxs(ArraySeq(intraBlock))
          .futureValue

        assertConflictStates(
          Seq(
            (blocks(0).hash, true, Some(ArraySeq(inputs(0).txHash))),
            (blocks(1).hash, false, None)
          )
        )

        // block2 will not be in main chain anymore, no conflicted txs now
        nodeBlocks = blocks.map { block =>
          block.copy(conflictedTxs = None)
        }

        // Kicking off block2 from main chain
        BlockDao.updateMainChainStatus(blocks(1).hash, false).futureValue

        ConflictedTxsService
          .handleConflictedTxs(ArraySeq(intraBlock))
          .futureValue

        // Both inputs are not conflicted anymore
        assertConflictStates(
          Seq(
            (blocks(0).hash, false, None),
            (blocks(1).hash, false, None)
          )
        )
      }
    }
  }

  trait Fixture {

    val intraChainIndex = ChainIndex(new GroupIndex(0), new GroupIndex(0))
    val intraBlock      = blockEntityGen(intraChainIndex).sample.get

    val blockGen = for {
      chain <- chainIndexGen
      block <- blockEntityGen(chain)
    } yield block

    implicit val blockCache: BlockCache             = TestBlockCache()
    implicit val transactionCache: TransactionCache = TestTransactionCache()

    def makeBlockFlowClient(
        blocks: Seq[BlockEntity],
        conflictedBlockHash: Option[BlockHash] = None,
        conflictedTxs: Option[ArraySeq[TransactionId]] = None
    ): BlockFlowClient = new EmptyBlockFlowClient {
      override def fetchBlock(fromGroup: GroupIndex, hash: BlockHash): Future[BlockEntity] = {
        blocks.find(_.hash == hash) match {
          case Some(block) if conflictedBlockHash.contains(hash) =>
            Future.successful(block.copy(conflictedTxs = conflictedTxs))
          case Some(block) =>
            Future.successful(block)
          case None => Future.failed(new Exception(s"Block not found: $hash"))
        }
      }
    }

    def assertNullConflictedInputs() = {
      exec(sql"SELECT COUNT(*) FROM inputs WHERE conflicted IS NOT NULL".as[Int]).head is 0
    }

    def insertInputs(inputs: InputEntity*) = {
      exec(InputQueries.insertInputs(ArraySeq.from(inputs)))
    }

    def assertConflictStates(
        expected: Seq[(BlockHash, Boolean, Option[ArraySeq[TransactionId]])]
    ) = {
      expected.foreach { case (blockHash, conflicted, conflictedTxs) =>
        exec(
          sql"SELECT conflicted FROM inputs WHERE block_hash = $blockHash".as[Boolean]
        ).head is conflicted
        exec(
          sql"SELECT conflicted_txs FROM block_headers WHERE hash = $blockHash"
            .as[Option[ArraySeq[TransactionId]]]
        ).head is conflictedTxs
      }
    }

    val groupIndex = GroupIndex.Zero
    val chainIndex = ChainIndex(groupIndex, groupIndex)

    def mainChainInput(): InputEntity = inputEntityGen().sample.get.copy(mainChain = true)

    def mainChainInput(outputRefKey: Hash): InputEntity =
      mainChainInput().copy(outputRefKey = outputRefKey)

    def setupMainChainBlocks(n: Int): (ArraySeq[BlockEntity], ArraySeq[InputEntity]) = {
      val blockHashes = List.fill(n)(blockHashGen.sample.get)
      val firstInput  = mainChainInput().copy(blockHash = blockHashes.head)
      val otherInputs = blockHashes.tail.map { hash =>
        mainChainInput(firstInput.outputRefKey).copy(blockHash = hash)
      }
      val blocks = blockHashes.zip(firstInput +: otherInputs).map { case (hash, input) =>
        blockEntityGen(chainIndex).sample.get.copy(hash = hash, inputs = ArraySeq(input))
      }

      insertMainChainBlocks(blocks)

      (blocks, firstInput +: otherInputs)
    }

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
    }
  }
}
