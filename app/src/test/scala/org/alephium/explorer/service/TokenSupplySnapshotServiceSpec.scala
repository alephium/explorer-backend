// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.time.Instant

import scala.collection.immutable.ArraySeq

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumFutureSpec, GroupSetting}
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model.{Pagination, TokenSupplySnapshot}
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.model.AppState.{
  LastFinalizedInputTime,
  LastTokenSupplyUpdate
}
import org.alephium.explorer.persistence.queries.AppStateQueries
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.{Address, ChainIndex, GroupIndex}
import org.alephium.util.{TimeStamp, U256}

class TokenSupplySnapshotServiceSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with TestDBRunner {
  implicit val gs: GroupSetting         = GroupSetting(1)
  implicit val groupConfig: GroupConfig = gs.groupConfig
  def ts(value: String): TimeStamp      = TimeStamp.unsafe(Instant.parse(value).toEpochMilli)
  val day1                              = ts("2021-11-08T23:59:59.999Z")
  val day2                              = ts("2021-11-09T23:59:59.999Z")
  val day3                              = ts("2021-11-10T23:59:59.999Z")
  def output(): TokenOutputEntity = tokenOutputEntityGen().sample.get.copy(
    timestamp = ts("2021-11-08T12:00:00Z"),
    mainChain = true,
    conflicted = None,
    lockTime = None,
    spentTimestamp = None,
    spentFinalized = None,
    amount = U256.unsafe(10)
  )

  "count every token's unspent outputs including reserved and locked addresses with exact boundaries" in {
    val first    = output()
    val second   = output()
    val maximum  = output().copy(amount = U256.MaxValue)
    val reserved = Address.fromBase58("122uvHwwcaWoXR1ryub9VK1yh2CZvYCqXxzsYDHRb2jYB").toOption.get
    def sameToken(): TokenOutputEntity = output().copy(token = first.token)
    val rows = ArraySeq(
      first,
      sameToken().copy(address = reserved, lockTime = Some(day3)),
      sameToken().copy(timestamp = day1, conflicted = Some(false)),
      sameToken().copy(spentTimestamp = Some(day2)),
      sameToken().copy(spentTimestamp = Some(day1)),
      sameToken().copy(timestamp = day2),
      sameToken().copy(mainChain = false),
      sameToken().copy(conflicted = Some(true)),
      second.copy(amount = U256.One, spentTimestamp = Some(day2)),
      maximum
    )
    exec(TokenOutputSchema.table ++= rows)
    exec(TokenSupplySnapshotService.snapshot(day1).transactionally)
    exec(TokenSupplySnapshotService.snapshot(day2).transactionally)
    exec(TokenSupplySnapshotService.snapshot(day2).transactionally)
    TokenSupplySnapshotService.list(first.token, Pagination.default).futureValue is
      ArraySeq(
        TokenSupplySnapshot(day2, U256.unsafe(40)),
        TokenSupplySnapshot(day1, U256.unsafe(40))
      )
    TokenSupplySnapshotService.list(second.token, Pagination.default).futureValue is
      ArraySeq(TokenSupplySnapshot(day2, U256.Zero), TokenSupplySnapshot(day1, U256.One))
    TokenSupplySnapshotService.list(maximum.token, Pagination.unsafe(1, 1)).futureValue is
      ArraySeq(TokenSupplySnapshot(day2, U256.MaxValue))
    TokenSupplySnapshotService.list(first.token, Pagination.unsafe(2, 1)).futureValue is
      ArraySeq(TokenSupplySnapshot(day1, U256.unsafe(40)))
    TokenSupplySnapshotService
      .list(output().token, Pagination.default)
      .futureValue is ArraySeq.empty
  }

  "checkpoint empty days and resume independently of existing ALPH history" in {
    val block  = blockEntityGen(ChainIndex(GroupIndex.unsafe(0), GroupIndex.unsafe(0))).sample.get
    val latest = LatestBlock.fromEntity(block).copy(timestamp = ts("2021-11-10T12:00:00Z"))
    exec(LatestBlockSchema.table += latest)
    exec(AppStateQueries.insertOrUpdate(LastFinalizedInputTime(day2)))
    exec(
      TokenSupplySchema.table += TokenSupplyEntity(day3, U256.Zero, U256.Zero, U256.Zero, U256.Zero)
    )
    TokenSupplySnapshotService.syncOnce().futureValue is ()
    exec(AppStateQueries.get(LastTokenSupplyUpdate)) is Some(LastTokenSupplyUpdate(day2))
    exec(TokenSupplySnapshotSchema.table.result) is Seq.empty
    val tokenOutput = output().copy(timestamp = ts("2021-11-10T00:00:00Z"))
    exec(TokenOutputSchema.table += tokenOutput)
    exec(LatestBlockSchema.table.update(latest.copy(timestamp = ts("2021-11-11T00:00:00Z"))))
    TokenSupplySnapshotService.syncOnce().futureValue is ()
    exec(AppStateQueries.get(LastTokenSupplyUpdate)) is Some(LastTokenSupplyUpdate(day2))
    exec(AppStateQueries.insertOrUpdate(LastFinalizedInputTime(day3)))
    TokenSupplySnapshotService.syncOnce().futureValue is ()
    TokenSupplySnapshotService.syncOnce().futureValue is ()
    TokenSupplySnapshotService.list(tokenOutput.token, Pagination.default).futureValue is
      ArraySeq(TokenSupplySnapshot(day3, U256.unsafe(10)))
    exec(AppStateQueries.get(LastTokenSupplyUpdate)) is Some(LastTokenSupplyUpdate(day3))
  }

  "wait until every chain has a latest block and never snapshot an incomplete day" in {
    val tokenOutput = output()
    exec(TokenOutputSchema.table += tokenOutput)
    TokenSupplySnapshotService.syncOnce().futureValue is ()
    exec(AppStateQueries.get(LastTokenSupplyUpdate)) is None
    val block = blockEntityGen(ChainIndex(GroupIndex.unsafe(0), GroupIndex.unsafe(0))).sample.get
    exec(LatestBlockSchema.table += LatestBlock.fromEntity(block).copy(timestamp = day1))
    TokenSupplySnapshotService.syncOnce().futureValue is ()
    exec(AppStateQueries.get(LastTokenSupplyUpdate)) is None
    TokenSupplySnapshotService
      .syncOnce()(executionContext, databaseConfig, GroupSetting(2))
      .futureValue is ()
    exec(AppStateQueries.get(LastTokenSupplyUpdate)) is None
  }

  "stop at the slowest chain even when finalization is further ahead" in {
    val groups = GroupSetting(2)
    val block  = blockEntityGen(ChainIndex(GroupIndex.unsafe(0), GroupIndex.unsafe(0))).sample.get
    val latest = LatestBlock.fromEntity(block)
    val rows = groups.chainIndexes.map { index =>
      val time =
        if (index.from.value == 0 && index.to.value == 0) ts("2021-11-09T12:00:00Z") else day3
      latest.copy(chainFrom = index.from, chainTo = index.to, timestamp = time)
    }
    exec(LatestBlockSchema.table ++= rows)
    exec(AppStateQueries.insertOrUpdate(LastFinalizedInputTime(day3)))
    exec(TokenOutputSchema.table += output())
    TokenSupplySnapshotService
      .syncOnce()(executionContext, databaseConfig, groups)
      .futureValue is ()
    exec(AppStateQueries.get(LastTokenSupplyUpdate)) is Some(LastTokenSupplyUpdate(day1))
  }

  "wait for finalization and exclude a day whose spent outputs are not completely finalized" in {
    exec(TokenOutputSchema.table += output())
    val block = blockEntityGen(ChainIndex(GroupIndex.unsafe(0), GroupIndex.unsafe(0))).sample.get
    exec(LatestBlockSchema.table += LatestBlock.fromEntity(block).copy(timestamp = day3))
    TokenSupplySnapshotService.syncOnce().futureValue is ()
    exec(AppStateQueries.get(LastTokenSupplyUpdate)) is None
    exec(AppStateQueries.insertOrUpdate(LastFinalizedInputTime(ts("2021-11-09T12:00:00Z"))))
    TokenSupplySnapshotService.syncOnce().futureValue is ()
    exec(AppStateQueries.get(LastTokenSupplyUpdate)) is Some(LastTokenSupplyUpdate(day1))
  }

  "roll back all token rows and checkpoint if a daily transaction fails" in {
    exec(TokenOutputSchema.table += output())
    val failing = for {
      _ <- TokenSupplySnapshotService.snapshot(day1)
      _ <- sqlu"SELECT 1 / 0"
    } yield ()
    execFailed(failing.transactionally)
    exec(TokenSupplySnapshotSchema.table.result) is Seq.empty
    exec(AppStateQueries.get(LastTokenSupplyUpdate)) is None
    exec(TokenSupplySnapshotService.snapshot(day1).transactionally)
    exec(TokenSupplySnapshotSchema.table.length.result) is 1
    exec(AppStateQueries.get(LastTokenSupplyUpdate)) is Some(LastTokenSupplyUpdate(day1))
  }
}
