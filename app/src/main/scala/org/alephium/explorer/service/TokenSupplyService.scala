// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.time.{Instant, LocalTime, ZonedDateTime, ZoneOffset}
import java.time.temporal.ChronoUnit

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.io.{Codec, Source}

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
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.Scheduler
import org.alephium.explorer.util.SlickUtil._
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
  def listTokenSupply(pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenSupply]]

  def getLatestTokenSupply()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TokenSupply]]
}

case object TokenSupplyService extends TokenSupplyService with StrictLogging {

  private val reservedAddresses =
    Source.fromResource("reserved_addresses")(Codec.UTF8).getLines().mkString

  private val launchDay =
    Instant.ofEpochMilli(ALPH.LaunchTimestamp.millis).truncatedTo(ChronoUnit.DAYS)

  def start(scheduleTime: LocalTime)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile],
      groupSetting: GroupSetting,
      scheduler: Scheduler
  ): Future[Unit] = {
    Future.successful {
      scheduler.scheduleDailyAt(
        taskId = TokenSupplyService.productPrefix,
        at = ZonedDateTime
          .ofInstant(Instant.EPOCH, ZoneOffset.UTC)
          .plusSeconds(scheduleTime.toSecondOfDay().toLong)
      )(syncOnce())
    }
  }

  def syncOnce()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      groupSetting: GroupSetting
  ): Future[Unit] = {
    logger.debug("Computing token supply")
    updateTokenSupply().map { _ =>
      logger.debug("Token supply updated")
    }
  }

  def listTokenSupply(pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenSupply]] = {
    run(
      sql"""
        SELECT *
        FROM token_supply
        ORDER BY block_timestamp DESC
      """
        .paginate(pagination)
        .asASE[TokenSupplyEntity](tokenSupplyGetResult)
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

  def getLatestTokenSupply()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TokenSupply]] =
    run(
      sql"""
        SELECT *
        FROM token_supply
        ORDER BY block_timestamp DESC
        LIMIT 1
      """.asASE[TokenSupplyEntity](tokenSupplyGetResult).headOrNone
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

  private def circulatingTokensOptionQuery(at: TimeStamp)(implicit
      ec: ExecutionContext
  ): DBActionR[Option[U256]] =
    sql"""
      SELECT sum(amount)
      FROM outputs
      WHERE block_timestamp <= $at
      AND (spent_timestamp > $at OR spent_timestamp IS NULL)
      AND (lock_time is NULL OR lock_time <= $at) /* Only count unlock tokens */
      AND main_chain = true
      AND #${notConflicted()}
      AND address NOT IN (#$reservedAddresses) /* We exclude the reserved wallets */
        """.asAS[Option[U256]].exactlyOne

  def circulatingTokensQuery(at: TimeStamp)(implicit ec: ExecutionContext): DBActionR[U256] =
    circulatingTokensOptionQuery(at).map(_.getOrElse(U256.Zero))

  private def allUnspentTokensOption(at: TimeStamp)(implicit
      ec: ExecutionContext
  ): DBActionR[Option[U256]] =
    sql"""
        SELECT sum(outputs.amount)
        FROM outputs
        WHERE outputs.main_chain = true
        AND #${notConflicted("outputs")}
        AND outputs.block_timestamp <=$at
        AND (spent_timestamp > $at OR spent_timestamp IS NULL)
      """.asAS[Option[U256]].exactlyOne

  def allUnspentTokensQuery(at: TimeStamp)(implicit ec: ExecutionContext): DBActionR[U256] =
    allUnspentTokensOption(at).map(_.getOrElse(U256.Zero))

  private def reservedTokensOptionQuery(at: TimeStamp)(implicit
      ec: ExecutionContext
  ): DBActionR[Option[U256]] =
    sql"""
       SELECT sum(outputs.amount)
       FROM outputs
       WHERE outputs.block_timestamp <= $at
       AND (spent_timestamp > $at OR spent_timestamp IS NULL)
       AND outputs.main_chain = true
       AND #${notConflicted("outputs")}
       AND outputs.address IN (#$reservedAddresses) /* We only take the reserved wallets */
        """.asAS[Option[U256]].exactlyOne

  private def reservedTokensQuery(at: TimeStamp)(implicit ec: ExecutionContext): DBActionR[U256] =
    reservedTokensOptionQuery(at).map(_.getOrElse(U256.Zero))

  private def lockedTokensOptionQuery(at: TimeStamp)(implicit
      ec: ExecutionContext
  ): DBActionR[Option[U256]] =
    sql"""
       SELECT sum(outputs.amount)
       FROM outputs
       WHERE outputs.block_timestamp <= $at
       AND outputs.lock_time > $at /* count only locked tokens */
       AND outputs.main_chain = true
       AND #${notConflicted("outputs")}
       AND outputs.address NOT IN (#$reservedAddresses) /* We exclude the reserved wallets */
        """.asAS[Option[U256]].exactlyOne

  private def lockedTokensQuery(at: TimeStamp)(implicit ec: ExecutionContext): DBActionR[U256] =
    lockedTokensOptionQuery(at).map(_.getOrElse(U256.Zero))

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def findMinimumLatestBlockTime()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      groupSetting: GroupSetting
  ): Future[Option[TimeStamp]] =
    run(
      DBIOAction.sequence(
        groupSetting.chainIndexes.map { chainIndex =>
          sql"""
              SELECT block_timestamp
              FROM latest_blocks
              WHERE chain_from = ${chainIndex.from}
              AND chain_to = ${chainIndex.to}
              LIMIT 1
            """.asAS[TimeStamp].headOrNone
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

  private def computeTokenSupply(
      at: TimeStamp
  )(implicit ec: ExecutionContext): DBActionR[(U256, U256, U256, U256)] =
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

  private def updateTokenSupply()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      groupSetting: GroupSetting
  ): Future[Unit] =
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
                _ <- insert(TokenSupplyEntity(day, total, circulating, reserved, locked))
              } yield (()))
            }.map(_ => ())
          }
    }

  private def initGenesisTokenSupply()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[TimeStamp] =
    run(for {
      (total, circulating, reserved, locked) <- computeTokenSupply(ALPH.LaunchTimestamp)
      _ <- insert(TokenSupplyEntity(ALPH.LaunchTimestamp, total, circulating, reserved, locked))
    } yield ALPH.LaunchTimestamp)

  private def insert(tokenSupply: TokenSupplyEntity) =
    TokenSupplySchema.table.insertOrUpdate(tokenSupply)

  private def getLatestTimestamp()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TimeStamp]] =
    run(
      sql"""
        SELECT block_timestamp
        FROM token_supply
        ORDER BY block_timestamp DESC
        LIMIT 1
      """.asAS[TimeStamp].headOrNone
    )

  private[service] def buildDaysRange(from: TimeStamp, until: TimeStamp): ArraySeq[TimeStamp] = {
    val fromDay  = Instant.ofEpochMilli(from.millis).truncatedTo(ChronoUnit.DAYS)
    val untilDay = Instant.ofEpochMilli(until.millis).truncatedTo(ChronoUnit.DAYS)

    val nbOfDays = fromDay.until(untilDay, ChronoUnit.DAYS)

    if (nbOfDays <= 0) {
      ArraySeq.empty
    } else {
      ArraySeq.range(1L, nbOfDays + 1).map { day =>
        TimeStamp
          .unsafe(fromDay.toEpochMilli)
          .plusUnsafe(Duration.ofDaysUnsafe(day))
          .minusUnsafe(Duration.ofMillisUnsafe(1))
      }
    }
  }
}
