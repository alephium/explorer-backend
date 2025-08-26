// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.BlockQueries
import org.alephium.explorer.persistence.queries.ConflictedTxsQueries._
import org.alephium.protocol.model.{BlockHash, TransactionId}
import org.alephium.util.TimeStamp

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.IterableOps"))
case object ConflictedTxsService extends StrictLogging {

  // TODO we could optimize by checking only the chain indexes that had intra groups
  def handleConflictedTxs(blocksWithEvents: ArraySeq[BlockEntity])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache
  ): Future[Unit] = {
    if (blocksWithEvents.exists(_.isIntraGroup())) {
      for {
        _ <- updateConflictedTxs(cache.getLastFinalizedTimestamp())
        // TODO do we want to check reorged conflicted txs at every new intra group?
        _ <- checkAndUpdateReorgedConflictedTxs(cache.getLastFinalizedTimestamp())
      } yield ()
    } else {
      Future.unit
    }
  }

  def updateConflictedTxs(from: TimeStamp)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient
  ): Future[Unit] = {
    logger.debug("Checking conflicted txs")
    dc.db.run(findConflictedTxs(from)).flatMap { conflictedTxs =>
      if (conflictedTxs.isEmpty) {
        logger.debug("No conflicted txs found")
        Future.unit
      } else {
        logger.debug("Conflicted txs found")
        foldFutures(conflictedTxs) { case (txHash, blockHash, conflicted) =>
          conflicted match {
            case Some(true) =>
              // Already flagged as conflicted, no need to update
              logger.debug(s"Tx $txHash already flagged as conflicted in block $blockHash")
              Future.unit
            case _ =>
              logger.debug(s"Checking tx $txHash in block $blockHash for conflicts")
              fetchBlockAndUpdateConflict(blockHash, txHash)
          }
        }.map(_ => ())
      }

    }
  }

  // TODO optimization could be done if inputs had `chainFrom` value
  def fetchBlockAndUpdateConflict(
      blockHash: BlockHash,
      txHash: TransactionId
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient
  ): Future[Unit] = {
    dc.db.run(BlockQueries.getBlockEntryLiteAction(blockHash)).flatMap {
      case None =>
        logger.error(s"Block $blockHash not found for conflicted tx $txHash")
        Future.unit
      case Some(block) =>
        blockFlowClient.fetchBlock(block.chainFrom, blockHash).flatMap { newBlock =>
          val conflicted = newBlock.conflictedTxs.map(_.contains(txHash)).getOrElse(false)
          updateConflict(txHash, conflicted, blockHash, newBlock.conflictedTxs)
        }
    }
  }

  def updateConflict(
      txHash: TransactionId,
      conflicted: Boolean,
      blockHash: BlockHash,
      conflictedTxs: Option[ArraySeq[TransactionId]]
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    logger.debug(
      s"Updating conflict status for tx $txHash in block $blockHash to $conflicted"
    )
    dc.db.run(
      (for {
        _ <- updateConflictStatus(txHash, blockHash, conflicted)
        _ <- updateBlockConflictTxs(blockHash, conflictedTxs)
      } yield ()).transactionally
    )
  }

  def checkAndUpdateReorgedConflictedTxs(
      timestamp: TimeStamp
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient
  ): Future[Unit] = {
    logger.debug("Checking and updating reorged conflicted txs")
    dc.db.run(findReorgedConflictedTxs(timestamp)).flatMap { txs =>
      if (txs.isEmpty) {
        logger.debug("No reorged conflicted txs found")
        Future.unit
      } else {
        logger.debug("Updated reorged conflicted txs found")
        foldFutures(txs) { case (txHash, blockHash) =>
          fetchBlockAndUpdateConflict(blockHash, txHash)
        }.map(_ => ())
      }
    }
  }
}
