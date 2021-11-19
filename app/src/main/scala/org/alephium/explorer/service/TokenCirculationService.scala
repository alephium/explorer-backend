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

import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import org.alephium.explorer.api.model.{GroupIndex, Height, Pagination, TokenCirculation}
import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.TokenCirculationEntity
import org.alephium.explorer.persistence.queries.TransactionQueries
import org.alephium.explorer.persistence.schema.{BlockHeaderSchema, TokenCirculationSchema}
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp, U256}

trait TokenCirculationService extends SyncService {
  def listTokenCirculation(pagination: Pagination): Future[Seq[TokenCirculation]]
  def getLatestTokenCirculation(): Future[Option[TokenCirculation]]
}

object TokenCirculationService {
  def apply(syncPeriod: Duration, config: DatabaseConfig[JdbcProfile], groupNum: Int)(
      implicit executionContext: ExecutionContext): TokenCirculationService =
    new Impl(syncPeriod, config, groupNum)

  private class Impl(val syncPeriod: Duration,
                     val config: DatabaseConfig[JdbcProfile],
                     groupNum: Int)(implicit val executionContext: ExecutionContext)
      extends TokenCirculationService
      with TransactionQueries
      with BlockHeaderSchema
      with TokenCirculationSchema
      with DBRunner
      with StrictLogging {
    import config.profile.api._

    private val launchDay =
      Instant.ofEpochMilli(ALPH.LaunchTimestamp.millis).truncatedTo(ChronoUnit.DAYS)

    private val chainIndexes: Seq[(GroupIndex, GroupIndex)] = for {
      i <- 0 to groupNum - 1
      j <- 0 to groupNum - 1
    } yield (GroupIndex.unsafe(i), GroupIndex.unsafe(j))

    def syncOnce(): Future[Unit] = {
      logger.debug("Computing token circulation")
      updateTokenCirculation().map { _ =>
        logger.debug("Token circulation updated")
      }
    }

    def listTokenCirculation(pagination: Pagination): Future[Seq[TokenCirculation]] = {
      val offset = pagination.offset.toLong
      val limit  = pagination.limit.toLong
      val toDrop = offset * limit
      run(
        tokenCirculationTable
          .sortBy { _.timestamp.desc }
          .drop(toDrop)
          .take(limit)
          .result
      ).map(_.map { entity =>
        TokenCirculation(
          entity.timestamp,
          entity.amount
        )
      })
    }

    def getLatestTokenCirculation(): Future[Option[TokenCirculation]] = {
      run(
        tokenCirculationTable
          .sortBy { _.timestamp.desc }
          .result
          .headOption
      ).map(_.map { entity =>
        TokenCirculation(
          entity.timestamp,
          entity.amount
        )
      })
    }

    private val mainInputs       = inputsTable.filter(_.mainChain)
    private val mainOutputs      = outputsTable.filter(_.mainChain)
    private val mainTransactions = transactionsTable.filter(_.mainChain)

    private def genesisLockedToken(lockTime: TimeStamp) = {
      mainOutputs
        .join(
          mainTransactions
            .join(blockHeadersTable.filter(_.height === Height.unsafe(0)))
            .on(_.blockHash === _.hash)
        )
        .on(_.txHash === _._1.hash)
        .filter { case (output, _) => output.lockTime.map(_ > lockTime).getOrElse(false) }
        .map { case (output, _) => output.amount }
        .sum
    }

    private def unspentTokens(at: TimeStamp) = {
      mainOutputs
        .filter(_.timestamp <= at)
        .joinLeft(mainInputs.filter(_.timestamp <= at))
        .on(_.key === _.outputRefKey)
        .filter { case (_, inputOpt) => inputOpt.isEmpty }
        .map { case (output, _) => output.amount }
        .sum
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    private def findMinimumLatestBlockTime(): Future[Option[TimeStamp]] = {
      run(
        DBIOAction.sequence(
          chainIndexes.map {
            case (from, to) =>
              blockHeadersTable
                .filter(header => header.chainFrom === from && header.chainTo === to)
                .sortBy(_.timestamp.desc)
                .map(_.timestamp)
                .result
                .headOption
          }
        )
      ).map { timestampsOpt =>
        if (timestampsOpt.contains(None)) {
          None
        } else {
          val min                   = timestampsOpt.flatten.min
          val mininumLatestBlockDay = Instant.ofEpochMilli(min).truncatedTo(ChronoUnit.DAYS)
          if (mininumLatestBlockDay == launchDay) {
            None
          } else {
            Some(TimeStamp.unsafe(min))
          }
        }
      }
    }

    private def computeTokenCirculation(at: TimeStamp, lockTime: TimeStamp): DBActionR[U256] = {
      for {
        unspent       <- unspentTokens(at).result
        genesisLocked <- genesisLockedToken(lockTime).result
      } yield (unspent.getOrElse(U256.Zero).subUnsafe(genesisLocked.getOrElse(U256.Zero)))
    }

    private def updateTokenCirculation(): Future[Unit] = {
      findMinimumLatestBlockTime().flatMap {
        case None =>
          Future.successful(())
        case Some(mininumLatestBlockTime) =>
          getLatestTimestamp()
            .flatMap {
              case None           => initGenesisTokenCirculation()
              case Some(latestTs) => Future.successful(latestTs)
            }
            .flatMap { latestTs =>
              val days = buildDaysRange(latestTs, mininumLatestBlockTime)
              foldFutures(days) { day =>
                run(for {
                  tokens <- computeTokenCirculation(day, day)
                  _      <- insert(TokenCirculationEntity(day, tokens))
                } yield (()))
              }
            }
      }
    }

    private def initGenesisTokenCirculation(): Future[TimeStamp] = {
      run(for {
        tokens <- computeTokenCirculation(ALPH.GenesisTimestamp, ALPH.LaunchTimestamp)
        _      <- insert(TokenCirculationEntity(ALPH.LaunchTimestamp, tokens))
      } yield (ALPH.LaunchTimestamp))
    }

    private def insert(tokenCirculation: TokenCirculationEntity) = {
      tokenCirculationTable.insertOrUpdate(tokenCirculation)
    }

    private def getLatestTimestamp(): Future[Option[TimeStamp]] = {
      run(
        tokenCirculationTable
          .sortBy { _.timestamp.desc }
          .map(_.timestamp)
          .result
          .headOption
      )
    }
  }

  private[service] def buildDaysRange(from: TimeStamp, until: TimeStamp): Seq[TimeStamp] = {
    val fromDay  = Instant.ofEpochMilli(from.millis).truncatedTo(ChronoUnit.DAYS)
    val untilDay = Instant.ofEpochMilli(until.millis).truncatedTo(ChronoUnit.DAYS)

    val nbOfDays = fromDay.until(untilDay, ChronoUnit.DAYS)

    if (nbOfDays <= 0) {
      Seq.empty
    } else {
      (1L to nbOfDays).map { day =>
        TimeStamp
          .unsafe(fromDay.toEpochMilli)
          .plusUnsafe(Duration.ofDaysUnsafe(day))
          .minusUnsafe(Duration.ofMillisUnsafe(1))
      }
    }
  }
}
