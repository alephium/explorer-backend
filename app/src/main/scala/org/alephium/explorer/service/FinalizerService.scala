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

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{foldFutures, Hash}
import org.alephium.explorer.api.model.Transaction
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp}

/*
 * Syncing mempool
 */

trait FinalizerService extends SyncService.BlockFlow

object FinalizerService extends StrictLogging {

  // scalastyle:off magic.number
  val finalizationDuration: Duration = Duration.ofSecondsUnsafe(6500)
  def finalizationTime: TimeStamp    = TimeStamp.now().minusUnsafe(finalizationDuration)
  val batchSize: Int                 = 1000
  // scalastyle:on magic.number

  def apply(syncPeriod: Duration, databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit executionContext: ExecutionContext): FinalizerService =
    new Impl(syncPeriod, databaseConfig)

  private class Impl(val syncPeriod: Duration, val databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit val executionContext: ExecutionContext)
      extends FinalizerService
      with DBRunner {

    override def syncOnce(): Future[Unit] = {
      logger.debug("Finalizing")
      finalizeOutputs(ALPH.LaunchTimestamp, finalizationTime, databaseConfig)
    }
  }

  def finalizeOutputs(from: TimeStamp,
                      to: TimeStamp,
                      databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit executionContext: ExecutionContext): Future[Unit] = {
    var i = 0
    DBRunner.run(databaseConfig)(getOutputsToUpdate(from, to)).flatMap { outputs =>
      logger.debug(s"Updating ${outputs.size} outputs")
      foldFutures(outputs.grouped(batchSize).toSeq) { sub =>
        DBRunner
          .run(databaseConfig)(DBIOAction.sequence(sub.toSeq.map {
            case (outputKey, txHash) => updateOutput(outputKey, txHash)
          }))
          .map { _ =>
            i = i + sub.size
            logger.debug(s"$i/${outputs.size} outputs updated")
          }
      }.map(_ => logger.debug(s"${outputs.size} outputs updated"))
    }
  }

  def getOutputsToUpdate(from: TimeStamp, to: TimeStamp): DBActionSR[(Hash, Transaction.Hash)] = {
    sql"""
      SELECT o.key, i.tx_hash
      FROM inputs i
      INNER JOIN outputs o
      ON i.output_ref_key = o.key
      WHERE o.spent_finalized IS NULL
      AND o.main_chain=true
      AND i.main_chain=true
      AND i.block_timestamp >= $from
      AND i.block_timestamp < $to;
    """.as[(Hash, Transaction.Hash)]
  }

  def updateOutput(outputKey: Hash, txHash: Transaction.Hash): DBActionR[Int] = {
    sqlu"""
      UPDATE outputs
      SET spent_finalized = $txHash
      WHERE key = $outputKey
    """
  }
}
