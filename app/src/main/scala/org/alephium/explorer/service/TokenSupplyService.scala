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
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model.{GroupIndex, Height, Pagination, TokenSupply}
import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.TokenSupplyEntity
import org.alephium.explorer.persistence.queries.TransactionQueries
import org.alephium.explorer.persistence.schema._
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp, U256}

trait TokenSupplyService extends SyncService {
  def listTokenSupply(pagination: Pagination): Future[Seq[TokenSupply]]
  def getLatestTokenSupply(): Future[Option[TokenSupply]]
}

object TokenSupplyService {
  def apply(syncPeriod: Duration, databaseConfig: DatabaseConfig[PostgresProfile], groupNum: Int)(
      implicit executionContext: ExecutionContext): TokenSupplyService =
    new Impl(syncPeriod, databaseConfig, groupNum)

  private class Impl(val syncPeriod: Duration,
                     val databaseConfig: DatabaseConfig[PostgresProfile],
                     groupNum: Int)(implicit val executionContext: ExecutionContext)
      extends TokenSupplyService
      with TransactionQueries
      with DBRunner
      with StrictLogging {

    private val launchDay =
      Instant.ofEpochMilli(ALPH.LaunchTimestamp.millis).truncatedTo(ChronoUnit.DAYS)

    private val chainIndexes: Seq[(GroupIndex, GroupIndex)] = for {
      i <- 0 to groupNum - 1
      j <- 0 to groupNum - 1
    } yield (GroupIndex.unsafe(i), GroupIndex.unsafe(j))

    def syncOnce(): Future[Unit] = {
      logger.debug("Computing token supply")
      updateTokenSupply().map { _ =>
        logger.debug("Token supply updated")
      }
    }

    def listTokenSupply(pagination: Pagination): Future[Seq[TokenSupply]] = {
      val offset = pagination.offset.toLong
      val limit  = pagination.limit.toLong
      val toDrop = offset * limit
      run(
        TokenSupplySchema.table
          .sortBy { _.timestamp.desc }
          .drop(toDrop)
          .take(limit)
          .result
      ).map(_.map { entity =>
        TokenSupply(
          entity.timestamp,
          entity.total,
          entity.circulating,
          ALPH.MaxALPHValue
        )
      })
    }

    def getLatestTokenSupply(): Future[Option[TokenSupply]] = {
      run(
        TokenSupplySchema.table
          .sortBy { _.timestamp.desc }
          .result
          .headOption
      ).map(_.map { entity =>
        TokenSupply(
          entity.timestamp,
          entity.total,
          entity.circulating,
          ALPH.MaxALPHValue
        )
      })
    }

    private val mainInputs  = InputSchema.table.filter(_.mainChain)
    private val mainOutputs = OutputSchema.table.filter(_.mainChain)

    private def genesisLockedToken(lockTime: TimeStamp) = {
      mainOutputs
        .join(
          mainTransactions
            .join(BlockHeaderSchema.table.filter(_.height === Height.unsafe(0)))
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
              BlockHeaderSchema.table
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
          val mininumLatestBlockDay = Instant.ofEpochMilli(min.millis).truncatedTo(ChronoUnit.DAYS)
          if (mininumLatestBlockDay == launchDay) {
            None
          } else {
            Some(min)
          }
        }
      }
    }

    private def computeTokenSupply(at: TimeStamp): DBActionR[(U256, U256)] = {
      for {
        unspent       <- unspentTokens(at).result
        genesisLocked <- genesisLockedToken(at).result
      } yield {
        val total = unspent.getOrElse(U256.Zero)
        (total, total.subUnsafe(genesisLocked.getOrElse(U256.Zero)))
      }
    }

    private def updateTokenSupply(): Future[Unit] = {
      findMinimumLatestBlockTime().flatMap {
        case None =>
          Future.successful(())
        case Some(mininumLatestBlockTime) =>
          getLatestTimestamp()
            .flatMap {
              case None           => initGenesisTokenSupply()
              case Some(latestTs) => Future.successful(latestTs)
            }
            .flatMap { latestTs =>
              val days = buildDaysRange(latestTs, mininumLatestBlockTime)
              foldFutures(days) { day =>
                run(for {
                  (total, circulating) <- computeTokenSupply(day)
                  _                    <- insert(TokenSupplyEntity(day, total, circulating))
                } yield (()))
              }.map(_ => ())
            }
      }
    }

    private def initGenesisTokenSupply(): Future[TimeStamp] = {
      run(for {
        (total, circulating) <- computeTokenSupply(ALPH.LaunchTimestamp)
        _                    <- insert(TokenSupplyEntity(ALPH.LaunchTimestamp, total, circulating))
      } yield (ALPH.LaunchTimestamp))
    }

    private def insert(tokenSupply: TokenSupplyEntity) = {
      TokenSupplySchema.table.insertOrUpdate(tokenSupply)
    }

    private def getLatestTimestamp(): Future[Option[TimeStamp]] = {
      run(
        TokenSupplySchema.table
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
