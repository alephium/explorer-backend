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
import slick.jdbc.JdbcProfile

import org.alephium.explorer.api.model.Height
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.TokenCirculationEntity
import org.alephium.explorer.persistence.queries.TransactionQueries
import org.alephium.explorer.persistence.schema.{BlockHeaderSchema, TokenCirculationSchema}
import org.alephium.util.{Duration, TimeStamp, U256}

trait TokenCirculationService extends SyncService

object TokenCirculationService {
  def apply(syncPeriod: Duration, config: DatabaseConfig[JdbcProfile])(
      implicit executionContext: ExecutionContext): TokenCirculationService =
    new Impl(syncPeriod, config)

  private class Impl(val syncPeriod: Duration, val config: DatabaseConfig[JdbcProfile])(
      implicit val executionContext: ExecutionContext)
      extends TokenCirculationService
      with TransactionQueries
      with BlockHeaderSchema
      with TokenCirculationSchema
      with DBRunner
      with StrictLogging {
    import config.profile.api._

    def syncOnce(): Future[Unit] = {
      logger.debug("Computing token circulation")
      updateTokenCirculation().map { _ =>
        logger.debug("Token circulation updated")
      }
    }

    private val mainInputs       = inputsTable.filter(_.mainChain)
    private val mainOutputs      = outputsTable.filter(_.mainChain)
    private val mainTransactions = transactionsTable.filter(_.mainChain)

    private def genesisLockedToken(ts: TimeStamp) = {
      mainOutputs
        .join(
          mainTransactions
            .join(blockHeadersTable.filter(_.height === Height.unsafe(0)))
            .on(_.blockHash === _.hash)
        )
        .on(_.txHash === _._1.hash)
        .filter { case (output, _) => output.lockTime.map(_ >= ts).getOrElse(false) }
        .map { case (output, _) => output.amount }
        .sum
    }

    private val unspentTokens =
      mainOutputs
        .joinLeft(mainInputs)
        .on(_.key === _.outputRefKey)
        .filter { case (_, inputOpt) => inputOpt.isEmpty }
        .map { case (output, _) => output.amount }
        .sum

    private def computeTokenCirculation(lockTime: TimeStamp): DBActionR[U256] = {
      for {
        unspent       <- unspentTokens.result
        genesisLocked <- genesisLockedToken(lockTime).result
      } yield (unspent.getOrElse(U256.Zero).subUnsafe(genesisLocked.getOrElse(U256.Zero)))
    }

    private def updateTokenCirculation(): Future[Unit] = {
      val now = TimeStamp.now()
      run(for {
        tokens <- computeTokenCirculation(now)
        _      <- tokenCirculationTable += TokenCirculationEntity(now, tokens)
      } yield (()))
    }
  }
}
