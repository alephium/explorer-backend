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

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model.AppState._
import org.alephium.explorer.persistence.queries.AppStateQueries
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.{Scheduler, TimeUtil}
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{BlockHash, TransactionId}
import org.alephium.util.{Duration, TimeStamp}

/*
 * Syncing mempool
 */

case object FinalizerService extends StrictLogging {

  final private case class UpdateResult(nbOfOutputs: Int, nbOfTransactions: Int)

  // scalastyle:off magic.number
  val finalizationDuration: Duration = Duration.ofSecondsUnsafe(6500)
  def finalizationTime: TimeStamp    = TimeStamp.now().minusUnsafe(finalizationDuration)
  def rangeStep: Duration            = Duration.ofHoursUnsafe(24)
  // scalastyle:on magic.number

  def start(interval: FiniteDuration)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      scheduler: Scheduler
  ): Future[Unit] =
    scheduler.scheduleLoop(
      taskId = FinalizerService.productPrefix,
      firstInterval = ScalaDuration.Zero,
      loopInterval = interval
    )(syncOnce())

  def syncOnce()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    logger.debug("Finalizing")
    finalizeOutputs()
  }

  def finalizeOutputs()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] =
    run(getStartEndTime()).flatMap {
      case Some((start, end)) =>
        finalizeOutputsWith(start, end, rangeStep)
      case None =>
        Future.successful(())
    }

  def finalizeOutputsWith(start: TimeStamp, end: TimeStamp, step: Duration)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    run(getFinalizedTxCountOrCount(start)).flatMap { txsCount =>
      finalizeOutputsFromToWithCount(start, end, step, txsCount)
    }
  }

  def finalizeOutputsFromToWithCount(
      start: TimeStamp,
      end: TimeStamp,
      step: Duration,
      txsCount: Int
  )(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    var updateCounter = 0
    var txsCounter    = txsCount
    logger.debug(s"Updating outputs")
    val timeRanges =
      TimeUtil.buildTimestampRange(start, end, step)
    foldFutures(timeRanges) { case (from, to) =>
      logger.debug(s"Updating outputs: ${TimeUtil.toInstant(from)} - ${TimeUtil.toInstant(to)}")
      run(
        (
          for {
            updateResult <- updateOutputs(from, to)
            _            <- updateLastFinalizedInputTime(to)
            _            <- updateFinalizedTxCount(txsCounter + updateResult.nbOfTransactions)
          } yield updateResult
        ).transactionally
      ).map { updateResult =>
        txsCounter = txsCounter + updateResult.nbOfTransactions
        updateCounter = updateCounter + updateResult.nbOfOutputs
        logger.debug(s"$updateCounter outputs updated")
      }
    }.map(_ => logger.debug(s"Outputs updated"))
  }

  /*
   * Update the `outputs` and `token_outputs` tables based on data from `inputs` within the specified time range.
   */
  private def updateOutputs(from: TimeStamp, to: TimeStamp)(implicit
      ec: ExecutionContext
  ): DBActionR[UpdateResult] =
    for {
      txs    <- findTransactions(from, to)
      inputs <- findInputs(txs)
      nb     <- updateOutputsWithInputs(inputs)
    } yield {
      UpdateResult(nb, txs.length)
    }

  /*
   * Search for transactions within the specified time range.
   */
  private def findTransactions(
      from: TimeStamp,
      to: TimeStamp
  ): StreamAction[(TransactionId, BlockHash, TimeStamp)] =
    sql"""
      SELECT hash, block_hash, block_timestamp
      FROM transactions
      WHERE main_chain = true
      AND block_timestamp >= $from
      AND block_timestamp <= $to;
      """.asAS[(TransactionId, BlockHash, TimeStamp)]

  /*
   * Search for inputs for the specified transactions.
   */
  private def findInputs(
      txs: ArraySeq[(TransactionId, BlockHash, TimeStamp)]
  )(implicit ec: ExecutionContext): DBActionR[ArraySeq[(TransactionId, TimeStamp, Hash)]] =
    DBIO
      .sequence(txs.map { case (txHash, blockHash, blockTimestamp) =>
        findInputsForTx(txHash, blockHash).map { outputRefKeys =>
          outputRefKeys.map { case outputRefKey =>
            (txHash, blockTimestamp, outputRefKey)
          }
        }
      })
      .map(_.flatten)

  /*
   * Search inputs for a given `txHash` and `blockHash` using the `inputs_tx_hash_block_hash_idx` index (txHash, blockHash).
   */
  private def findInputsForTx(txId: TransactionId, blockHash: BlockHash): StreamAction[Hash] =
    sql"""
      SELECT output_ref_key
      FROM inputs
      WHERE main_chain = true
      AND tx_hash = $txId
      AND block_hash = $blockHash;
      """.asAS[Hash]

  /*
   * Update `outputs` and `token_outputs` tables with `txHash` and `blockTimestamp`
   * based on their corresponding `outputRefKeys`.
   */
  private def updateOutputsWithInputs(
      inputs: ArraySeq[(TransactionId, TimeStamp, Hash)]
  )(implicit ec: ExecutionContext): DBActionR[Int] =
    DBIO
      .sequence(inputs.map { case (txHash, blockTimestamp, outputRefKey) =>
        for {
          nb <- updateOutput(txHash, blockTimestamp, outputRefKey)
          _  <- updateTokenOutput(txHash, blockTimestamp, outputRefKey)
        } yield nb
      })
      .map(_.sum)

  /*
   * Update the `output` with `txHash` and `blockTimestamp`
   * for the specified `outputRefKey` using the primary key index.
   */
  private def updateOutput(
      txHash: TransactionId,
      blockTimestamp: TimeStamp,
      outputRefKey: Hash
  ): DBActionR[Int] =
    sqlu"""
    UPDATE outputs
    SET spent_finalized = $txHash, spent_timestamp = $blockTimestamp
    WHERE key = $outputRefKey
    AND main_chain = true
  """

  /*
   * Update the `token_output` with `txHash` and `blockTimestamp`
   * for the specified `outputRefKey` using the primary key index.
   */
  private def updateTokenOutput(
      txHash: TransactionId,
      blockTimestamp: TimeStamp,
      outputRefKey: Hash
  ): DBActionR[Int] =
    sqlu"""
      UPDATE token_outputs
      SET spent_finalized = $txHash, spent_timestamp = $blockTimestamp
      WHERE key = $outputRefKey
      AND main_chain = true
    """

  def getFinalizedTxCountOrCount(upTo: TimeStamp)(implicit
      executionContext: ExecutionContext
  ): DBActionR[Int] =
    AppStateQueries.get(FinalizedTxCount).flatMap {
      case Some(FinalizedTxCount(count)) => DBIOAction.successful(count)
      case None                          => countTxsUpTo(upTo)
    }

  def getStartEndTime()(implicit
      executionContext: ExecutionContext
  ): DBActionR[Option[(TimeStamp, TimeStamp)]] = {
    val ft = finalizationTime
    getMaxInputsTs.flatMap(_ match {
      // No input in db
      case None | Some(TimeStamp.zero) => DBIOAction.successful(None)
      case Some(_end) =>
        val end = if (_end.isBefore(ft)) _end else ft
        getLastFinalizedInputTime().flatMap {
          case Some(lastFinalizedInputTime) =>
            // Inputs at finalization time are already finalized, so adding 1 millis
            // Re-finalizing isn't an issue, but it is a waste of resources
            DBIOAction.successful(Some((lastFinalizedInputTime + Duration.ofMillisUnsafe(1), end)))
          case None =>
            getMinInputsTs.map {
              // No input in db
              case None | Some(TimeStamp.zero)       => None
              case Some(start) if ft.isBefore(start) =>
                // inputs are only after finalization time, noop
                None
              case Some(start) =>
                Some((start, end))
            }
        }
    })
  }

  private def getMinInputsTs(implicit ec: ExecutionContext): DBActionR[Option[TimeStamp]] =
    sql"""
      SELECT MIN(block_timestamp) FROM inputs WHERE main_chain = true
    """.asAS[TimeStamp].headOrNone

  private def getMaxInputsTs(implicit ec: ExecutionContext): DBActionR[Option[TimeStamp]] =
    sql"""
      SELECT MIN(block_timestamp)
      FROM latest_blocks
    """.asAS[TimeStamp].headOrNone

  private def countTxsUpTo(time: TimeStamp): DBActionR[Int] = {
    sql"""
        SELECT COUNT(*)
        FROM transactions
        WHERE block_timestamp < $time
        AND main_chain = true
        """.as[Int].head
  }

  private def getLastFinalizedInputTime()(implicit
      executionContext: ExecutionContext
  ): DBActionR[Option[TimeStamp]] =
    AppStateQueries.get(LastFinalizedInputTime).map(_.map(_.time))

  private def updateLastFinalizedInputTime(time: TimeStamp) =
    AppStateQueries.insertOrUpdate(LastFinalizedInputTime(time))

  private def updateFinalizedTxCount(txCount: Int) =
    AppStateQueries.insertOrUpdate(FinalizedTxCount(txCount))
}
