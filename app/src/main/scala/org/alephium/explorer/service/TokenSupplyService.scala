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
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}
import scala.io.Source

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{foldFutures, GroupSetting}
import org.alephium.explorer.api.model.{Pagination, TokenSupply}
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model.TokenSupplyEntity
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.Scheduler
import org.alephium.explorer.util.SlickSugar._
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp, U256}

/*
 * Token supply service.
 *
 * Circulating supply computation follows CoinMarketCap:
 *
 * Circulating supply = Total supply - Balances from reserved wallet - Locked tokens
 *
 * Reserved wallets can be:
 *   Burn
 *   Escrow
 *   Private sale investors
 *   Marketing operations
 *   Treasury
 *   Ecosystem incentives
 *   Team/advisor/contractors
 *   Team-conrolled assets
 *
 * Most of those wallets are included in our genesis blocks, so we don't take those blocks when computing the circulating supply,
 * some other wallets were creating after the launch and are explicitly listed below in `reservedAddresses`.
 */
trait TokenSupplyService {
  def listTokenSupply(pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[TokenSupply]]

  def getLatestTokenSupply()(implicit ec: ExecutionContext,
                             dc: DatabaseConfig[PostgresProfile]): Future[Option[TokenSupply]]
}

case object TokenSupplyService extends TokenSupplyService with StrictLogging {

  private val reservedAddresses = Source.fromResource("reserved_addresses").getLines().mkString

  private val launchDay =
    Instant.ofEpochMilli(ALPH.LaunchTimestamp.millis).truncatedTo(ChronoUnit.DAYS)

  def start(interval: FiniteDuration)(implicit executionContext: ExecutionContext,
                                      databaseConfig: DatabaseConfig[PostgresProfile],
                                      groupSetting: GroupSetting,
                                      scheduler: Scheduler): Future[Unit] =
    scheduler.scheduleLoop(
      taskId        = TokenSupplyService.productPrefix,
      firstInterval = ScalaDuration.Zero,
      loopInterval  = interval
    )(syncOnce())

  def syncOnce()(implicit ec: ExecutionContext,
                 dc: DatabaseConfig[PostgresProfile],
                 groupSetting: GroupSetting): Future[Unit] = {
    logger.debug("Computing token supply")
    updateTokenSupply().map { _ =>
      logger.debug("Token supply updated")
    }
  }

  def listTokenSupply(pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[TokenSupply]] = {
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
        entity.reserved,
        entity.locked,
        ALPH.MaxALPHValue
      )
    })
  }

  def getLatestTokenSupply()(implicit ec: ExecutionContext,
                             dc: DatabaseConfig[PostgresProfile]): Future[Option[TokenSupply]] =
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
        entity.reserved,
        entity.locked,
        ALPH.MaxALPHValue
      )
    })

  private def circulatingTokensOptionQuery(at: TimeStamp)(
      implicit ec: ExecutionContext): DBActionR[Option[U256]] =
    sql"""
      SELECT sum(outputs.amount)
      FROM outputs
      LEFT JOIN inputs
        ON outputs.key = inputs.output_ref_key
        AND inputs.main_chain = true
        AND inputs.block_timestamp <= $at
      WHERE outputs.block_timestamp <= $at
      AND (outputs.lock_time is NULL OR outputs.lock_time <= $at) /* Only count unlock tokens */
      AND outputs.main_chain = true
      AND outputs.address NOT IN (#$reservedAddresses) /* We exclude the reserved wallets */
      AND inputs.block_hash IS NULL;
        """.as[Option[U256]].exactlyOne

  def circulatingTokensQuery(at: TimeStamp)(implicit ec: ExecutionContext): DBActionR[U256] =
    circulatingTokensOptionQuery(at).map(_.getOrElse(U256.Zero))

  private def allUnspentTokensOption(at: TimeStamp)(
      implicit ec: ExecutionContext): DBActionR[Option[U256]] =
    sql"""
        SELECT sum(outputs.amount)
        FROM outputs
        LEFT JOIN inputs
          ON outputs.key = inputs.output_ref_key
          AND inputs.main_chain = true
          AND inputs.block_timestamp <= $at
        WHERE outputs.main_chain = true
        AND outputs.block_timestamp <= $at
        AND inputs.block_hash IS NULL;
      """.as[Option[U256]].exactlyOne

  def allUnspentTokensQuery(at: TimeStamp)(implicit ec: ExecutionContext): DBActionR[U256] =
    allUnspentTokensOption(at).map(_.getOrElse(U256.Zero))

  private def reservedTokensOptionQuery(at: TimeStamp)(
      implicit ec: ExecutionContext): DBActionR[Option[U256]] =
    sql"""
       SELECT sum(outputs.amount)
       FROM outputs
       LEFT JOIN inputs
         ON outputs.key = inputs.output_ref_key
         AND inputs.main_chain = true
         AND inputs.block_timestamp <= $at
       WHERE outputs.block_timestamp <= $at
       AND outputs.main_chain = true
      AND outputs.address IN (#$reservedAddresses) /* We only take the reserved wallets */
      AND inputs.block_hash IS NULL;
        """.as[Option[U256]].exactlyOne

  private def reservedTokensQuery(at: TimeStamp)(implicit ec: ExecutionContext): DBActionR[U256] =
    reservedTokensOptionQuery(at).map(_.getOrElse(U256.Zero))

  private def lockedTokensOptionQuery(at: TimeStamp)(
      implicit ec: ExecutionContext): DBActionR[Option[U256]] =
    sql"""
       SELECT sum(outputs.amount)
       FROM outputs
       LEFT JOIN inputs
         ON outputs.key = inputs.output_ref_key
         AND inputs.main_chain = true
         AND inputs.block_timestamp <= $at
       WHERE outputs.block_timestamp <= $at
       AND outputs.lock_time > $at /* count only locked tokens */
       AND outputs.main_chain = true
      AND outputs.address NOT IN (#$reservedAddresses) /* We exclude the reserved wallets */
      AND inputs.block_hash IS NULL;
        """.as[Option[U256]].exactlyOne

  private def lockedTokensQuery(at: TimeStamp)(implicit ec: ExecutionContext): DBActionR[U256] =
    lockedTokensOptionQuery(at).map(_.getOrElse(U256.Zero))

  @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
  private def findMinimumLatestBlockTime()(implicit ec: ExecutionContext,
                                           dc: DatabaseConfig[PostgresProfile],
                                           groupSetting: GroupSetting): Future[Option[TimeStamp]] =
    run(
      DBIOAction.sequence(
        groupSetting.groupIndexes.map {
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

  private def computeTokenSupply(at: TimeStamp)(
      implicit ec: ExecutionContext): DBActionR[(U256, U256, U256, U256)] =
    for {
      total       <- allUnspentTokensQuery(at)
      circulating <- circulatingTokensQuery(at)
      reserved    <- reservedTokensQuery(at)
      locked      <- lockedTokensQuery(at)
    } yield {
      val zero = total.subUnsafe(circulating).subUnsafe(reserved).subUnsafe(locked)
      if (zero != U256.Zero) {
        logger.error(s"Wrong supply computed at $at")
      }
      (total, circulating, reserved, locked)
    }

  private def updateTokenSupply()(implicit ec: ExecutionContext,
                                  dc: DatabaseConfig[PostgresProfile],
                                  groupSetting: GroupSetting): Future[Unit] =
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
                (total, circulating, reserved, locked) <- computeTokenSupply(day)
                _                                      <- insert(TokenSupplyEntity(day, total, circulating, reserved, locked))
              } yield (()))
            }.map(_ => ())
          }
    }

  private def initGenesisTokenSupply()(implicit ec: ExecutionContext,
                                       dc: DatabaseConfig[PostgresProfile]): Future[TimeStamp] =
    run(for {
      (total, circulating, reserved, locked) <- computeTokenSupply(ALPH.LaunchTimestamp)
      _                                      <- insert(TokenSupplyEntity(ALPH.LaunchTimestamp, total, circulating, reserved, locked))
    } yield ALPH.LaunchTimestamp)

  private def insert(tokenSupply: TokenSupplyEntity) =
    TokenSupplySchema.table.insertOrUpdate(tokenSupply)

  private def getLatestTimestamp()(implicit ec: ExecutionContext,
                                   dc: DatabaseConfig[PostgresProfile]): Future[Option[TimeStamp]] =
    run(
      TokenSupplySchema.table
        .sortBy { _.timestamp.desc }
        .map(_.timestamp)
        .result
        .headOption
    )

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
