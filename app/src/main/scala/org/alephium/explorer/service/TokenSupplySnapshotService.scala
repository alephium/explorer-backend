// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{foldFutures, GroupSetting}
import org.alephium.explorer.api.model.{Pagination, TokenSupplySnapshot}
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model.AppState.{
  LastFinalizedInputTime,
  LastTokenSupplyUpdate
}
import org.alephium.explorer.persistence.queries.AppStateQueries
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.persistence.schema.TokenSupplySnapshotSchema
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.TokenId
import org.alephium.util.{Duration, TimeStamp}

object TokenSupplySnapshotService {
  def list(token: TokenId, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenSupplySnapshot]] =
    run(
      TokenSupplySnapshotSchema.table
        .filter(_.token === token)
        .sortBy(_.timestamp.desc)
        .drop(pagination.offset)
        .take(pagination.limit)
        .result
    )
      .map(rows => ArraySeq.from(rows.map(row => TokenSupplySnapshot(row.timestamp, row.total))))

  def syncOnce()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      groupSetting: GroupSetting
  ): Future[Unit] =
    TokenSupplyService.findMinimumLatestBlockTime().flatMap {
      case None => Future.successful(())
      case Some(latestBlock) =>
        run(for {
          checkpoint <- AppStateQueries.get(LastTokenSupplyUpdate)
          finalized  <- AppStateQueries.get(LastFinalizedInputTime)
        } yield (checkpoint, finalized)).flatMap {
          case (_, None)                     => Future.successful(())
          case (checkpoint, Some(finalized)) =>
            // Finalizer writes spent timestamps asynchronously. Only completed, finalized days are safe.
            val finalizedUntil = finalized.time.plusUnsafe(Duration.ofMillisUnsafe(1))
            val until          = if (latestBlock < finalizedUntil) latestBlock else finalizedUntil
            val from = checkpoint
              .map(_.time.plusUnsafe(Duration.ofMillisUnsafe(1)))
              .getOrElse(ALPH.LaunchTimestamp)
            foldFutures(TokenSupplyService.buildDaysRange(from, until)) { day =>
              run(snapshot(day).transactionally)
            }.map(_ => ())
        }
    }

  private[service] def snapshot(at: TimeStamp)(implicit ec: ExecutionContext): DBActionW[Unit] =
    for {
      // Keep spent rows in the grouping so a fully burned token gets an explicit zero snapshot.
      // Reserved addresses and locked outputs count exactly like every other unspent output.
      _ <- sqlu"""
        INSERT INTO token_supply_snapshots (token, block_timestamp, total)
        SELECT token, $at,
          sum(CASE WHEN spent_timestamp IS NULL OR spent_timestamp > $at THEN amount ELSE 0 END)
        FROM token_outputs
        WHERE main_chain = true AND #${notConflicted()} AND block_timestamp <= $at
        GROUP BY token
        ON CONFLICT (token, block_timestamp) DO UPDATE SET total = EXCLUDED.total
      """
      _ <- AppStateQueries.insertOrUpdate(LastTokenSupplyUpdate(at))
    } yield ()
}
