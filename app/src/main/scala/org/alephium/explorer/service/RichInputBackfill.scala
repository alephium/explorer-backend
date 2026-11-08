// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.persistence.model.InputEntity
import org.alephium.explorer.persistence.queries.{BlockQueries, InputIndexQueries}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.persistence.schema.InputSchema
import org.alephium.protocol.model.BlockHash

/** Finish incomplete legacy inputs before any synchronization/finalization service starts. Each
  * block commits independently; missing amounts are the resumable work queue.
  */
object RichInputBackfill extends StrictLogging {
  // scalastyle:off magic.number
  private val batchSize = 100
  // scalastyle:on magic.number

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def run()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      client: BlockFlowClient
  ): Future[Unit] = {
    val pending = InputSchema.table
      .filter(_.outputRefAmount.isEmpty)
      .map(_.blockHash)
      .distinct
      .sorted
      .take(batchSize)
      .result
    dc.db.run(pending).flatMap { hashes =>
      if (hashes.isEmpty) {
        Future.unit
      } else {
        logger.info(
          s"Backfilling rich inputs for ${hashes.size} legacy blocks before starting synchronization"
        )
        hashes
          .foldLeft(Future.unit) { (previous, hash) =>
            previous.flatMap(_ => repair(hash))
          }
          .flatMap(_ => run())
      }
    }
  }

  private def repair(hash: BlockHash)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      client: BlockFlowClient
  ): Future[Unit] =
    dc.db.run(BlockQueries.getBlockHeaderAction(hash)).flatMap {
      case None =>
        Future.failed(new IllegalStateException(s"Missing header for rich input backfill: $hash"))
      case Some(header) =>
        client.fetchBlock(header.chainFrom, hash).flatMap { block =>
          dc.db.run(repairInputs(hash, block.inputs).transactionally)
        }
    }

  private[explorer] def repairInputs(hash: BlockHash, richInputs: Iterable[InputEntity])(implicit
      ec: ExecutionContext
  ): DBIO[Unit] = {
    val missing = InputSchema.table.filter(i => i.blockHash === hash && i.outputRefAmount.isEmpty)
    missing.result.flatMap { rows =>
      DBIO
        .sequence(rows.map { row =>
          richInputs.find(i =>
            i.blockHash == row.blockHash && i.txHash == row.txHash &&
              i.outputRefKey == row.outputRefKey && i.outputRefAmount.isDefined && i.outputRefAddress.isDefined
          ) match {
            case None =>
              DBIO.failed(
                new IllegalStateException(
                  s"Rich block lacks legacy input: $hash / ${row.outputRefKey}"
                )
              )
            case Some(rich) =>
              val selected = InputSchema.table.filter(i =>
                i.blockHash === hash && i.outputRefKey === row.outputRefKey
              )
              for {
                _ <- selected
                  .filter(_.outputRefAmount.isEmpty)
                  .map(i =>
                    (
                      i.outputRefTxHash,
                      i.outputRefAddress,
                      i.outputRefGrouplessAddress,
                      i.outputRefAmount,
                      i.outputRefTokens
                    )
                  )
                  .update(
                    (
                      rich.outputRefTxHash,
                      rich.outputRefAddress,
                      rich.outputRefGrouplessAddress,
                      rich.outputRefAmount,
                      rich.outputRefTokens
                    )
                  )
                persisted <- selected.result
                _         <- InputIndexQueries.insert(persisted)
              } yield ()
          }
        })
        .map(_ => ())
    }
  }
}
