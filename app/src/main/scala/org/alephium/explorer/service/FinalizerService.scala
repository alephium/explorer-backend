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
  private val rangeStep              = Duration.ofMinutesUnsafe(60)
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

  @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
  def finalizeOutputs(from: TimeStamp,
                      to: TimeStamp,
                      databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit executionContext: ExecutionContext): Future[Unit] = {
    var i = 0
    DBRunner.run(databaseConfig)(getMinInputsTs()).flatMap { mins =>
      logger.debug(s"Updating outputs")
      val min        = mins.head
      val timeRanges = BlockFlowSyncService.buildTimestampRange(min, finalizationTime, rangeStep)
      foldFutures(timeRanges) {
        case (from, to) =>
          DBRunner
            .run(databaseConfig)(updateOutputs(from, to))
            .map { nb =>
              i = i + nb
              logger.debug(s"$i outputs updated")
            }
      }.map(_ => ())
    }
  }

  def getMinInputsTs(): DBActionSR[TimeStamp] = {
    sql"""
    SELECT MIN(block_timestamp) FROM inputs
    """.as[TimeStamp]
  }

  def updateOutputs(from: TimeStamp, to: TimeStamp): DBActionR[Int] = {
    sqlu"""
      UPDATE outputs o
      SET spent_finalized = i.tx_hash
      FROM inputs i
      WHERE i.output_ref_key = o.key
      AND o.main_chain=true
      AND i.main_chain=true
      AND i.block_timestamp >= $from
      AND i.block_timestamp < $to;
      """
  }
}
