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

import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.schema.CustomSetParameter.TimeStampSetParameter
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
      run(finalizeOutputs(finalizationTime))
    }
  }

  def finalizeOutputs(before: TimeStamp)(
      implicit executionContext: ExecutionContext): DBActionR[Unit] = {
    for {
      toUpdate <- updateSpentTable(before)
      _ = logger.debug(s"$toUpdate outputs to update")
      _ <- updateSpentOutput(toUpdate)
    } yield {
      logger.debug(s"Outputs finalized")
    }
  }

  def updateSpentTable(before: TimeStamp): DBActionR[Int] = {
    sqlu"""
  INSERT INTO temp_spent
   SELECT ROW_NUMBER() OVER(ORDER BY key), o.key, i.tx_hash
    FROM outputs o
    INNER JOIN inputs i
    ON o.key = i.output_ref_key
    WHERE o.spent_finalized IS NULL
    AND o.main_chain=true
    AND i.main_chain=true
    AND i.block_timestamp < $before;

    """
  }

  /*
   * Update `spent_finalized` field
   * The SQL Procedure would be the preferd solution, but is not used now as we can't get the logging working when running from the scala app,
   */
  private def updateSpentOutput(totalRecords: Int)(
      implicit executionContext: ExecutionContext): DBActionR[Unit] = {
    if (totalRecords > 0) {
      logger.info(s"Updating $totalRecords outputs")
      for {
        _ <- updateLoop(totalRecords, 0)
        _ <- sqlu"""TRUNCATE TABLE temp_spent"""
      } yield (())
    } else {
      DBIOAction.successful(())
    }
  }

  private def updateLoop(totalRecords: Int, counter: Int)(
      implicit executionContext: ExecutionContext): DBActionR[Int] = {
    if (counter <= totalRecords) {
      update(counter, totalRecords)
    } else {
      DBIOAction.successful(0)
    }
  }

  private def update(counter: Int, totalRecords: Int)(
      implicit executionContext: ExecutionContext): DBActionR[Int] = {
    logger.info(s"Updating $counter/$totalRecords")
    sqlu"""
      UPDATE outputs o
      SET spent_finalized = ts.tx_hash
      FROM temp_spent ts
      WHERE ts.key = o.key
      AND ts.row_number > $counter AND ts.row_number <= $counter+#$batchSize
    """.flatMap { _ =>
      updateLoop(totalRecords, counter + batchSize)
    }
  }
}
