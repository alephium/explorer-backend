// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import java.math.BigInteger

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import org.apache.pekko.util.ByteString
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer._
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache, TransactionCache}
import org.alephium.explorer.config.BootMode
import org.alephium.explorer.persistence.{Database, DatabaseFixtureForAll, Migrations}
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.explorer.service._
import org.alephium.protocol.{ALPH, Hash}
import org.alephium.protocol.model.{BlockHash, ChainIndex, GroupIndex}
import org.alephium.util.{Duration, TimeStamp}

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class InfosServerSpec()
    extends AlephiumFutureSpec
    with HttpServerFixture
    with DatabaseFixtureForAll {

  val tokenSupply = TokenSupply(
    TimeStamp.zero,
    ALPH.alph(1),
    ALPH.alph(2),
    ALPH.alph(3),
    ALPH.alph(4),
    ALPH.alph(5)
  )

  val tokenSupplyService = new TokenSupplyService {
    def listTokenSupply(pagination: Pagination)(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[TokenSupply]] =
      Future.successful(
        ArraySeq(
          tokenSupply
        )
      )

    def getLatestTokenSupply()(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[Option[TokenSupply]] =
      Future.successful(
        Some(
          tokenSupply
        )
      )

  }
  implicit val blockCache: BlockCache = TestBlockCache()
  implicit val transactionCache: TransactionCache = TransactionCache(
    new Database(BootMode.ReadWrite)
  )
  val transactionService = new EmptyTransactionService {
    override def getTotalNumber()(implicit cache: TransactionCache): Int = 10
  }

  val infoServer =
    new InfosServer(tokenSupplyService, BlockService, transactionService)

  val routes = infoServer.routes

  "return the explorer infos" in {
    Get(s"/infos") check { response =>
      response.as[ExplorerInfo] is ExplorerInfo(
        BuildInfo.releaseVersion,
        BuildInfo.commitId,
        Migrations.latestVersion.version,
        TimeStamp.zero,
        TimeStamp.zero
      )
    }
  }

  "return chains heights" in new Fixture {
    val chainIndex =
      groupSetting.chainIndexes.find(ci => ci.from.value == 1 && ci.to.value == 1).get
    insertBlock(
      makeBlock(
        chainIndex,
        height = 3,
        timestamp = TimeStamp.now().plusUnsafe(Duration.ofHoursUnsafe(3))
      )
    )

    Get(s"/infos/heights") check { response =>
      response
        .as[ArraySeq[PerChainHeight]]
        .find(ph => ph.chainFrom == chainIndex.from.value && ph.chainTo == chainIndex.to.value)
        .get is
        PerChainHeight(
          chainIndex.from.value,
          chainIndex.to.value,
          3L,
          3L
        )
    }
  }

  "return the token supply list" in {
    Get(s"/infos/supply") check { response =>
      response.as[ArraySeq[TokenSupply]] is ArraySeq(tokenSupply)
    }
  }

  "return the token current supply" in {
    Get(s"/infos/supply/circulating-alph") check { response =>
      val circulating = response.as[Int]
      circulating is 2
    }
  }

  "return the total token supply" in {
    Get(s"/infos/supply/total-alph") check { response =>
      val total = response.as[Int]
      total is 1
    }
  }

  "return the reserved token supply" in {
    Get(s"/infos/supply/reserved-alph") check { response =>
      val reserved = response.as[Int]
      reserved is 3
    }
  }

  "return the locked token supply" in {
    Get(s"/infos/supply/locked-alph") check { response =>
      val locked = response.as[Int]
      locked is 4
    }
  }

  "return the total transactions number" in {
    Get(s"/infos/total-transactions") check { response =>
      val total = response.as[Int]
      total is 10
    }
  }

  "return the average block times" in new Fixture {
    seedLatestBlocks(true)
    val zeroChainIndex =
      groupSetting.chainIndexes
        .find(ci => ci.from == GroupIndex.Zero && ci.to == GroupIndex.Zero)
        .get
    val base = TimeStamp.now().plusUnsafe(Duration.ofHoursUnsafe(3))
    insertBlocks(
      makeBlock(zeroChainIndex, height = 0, timestamp = base),
      makeBlock(
        zeroChainIndex,
        height = 1,
        timestamp = base.plusUnsafe(Duration.ofMinutesUnsafe(2))
      ),
      makeBlock(
        zeroChainIndex,
        height = 2,
        timestamp = base.plusUnsafe(Duration.ofMinutesUnsafe(4))
      )
    )

    Get(s"/infos/average-block-times") check { response =>
      response.as[ArraySeq[PerChainDuration]].find(_.chainFrom == 0).get is PerChainDuration(
        0,
        0,
        Duration.ofMinutesUnsafe(2).millis,
        Duration.ofMinutesUnsafe(2).millis
      )
    }
  }

  class Fixture {
    def seedLatestBlocks(excludeChainZero: Boolean): Unit = {
      groupSetting.chainIndexes.foreach { chainIndex =>
        if (
          !(excludeChainZero && chainIndex.from == GroupIndex.Zero && chainIndex.to == GroupIndex.Zero)
        ) {
          val block = makeBlock(
            chainIndex,
            height = 0,
            timestamp = TimeStamp.now().plusUnsafe(Duration.ofHoursUnsafe(3))
          )
          insertBlock(block)
        }
      }
    }

    def insertBlock(block: BlockEntity): Unit = {
      BlockDao.insert(block).futureValue
      BlockDao.updateLatestBlock(block).futureValue
    }

    def insertBlocks(blocks: BlockEntity*): Unit = {
      BlockDao.insertAll(ArraySeq.from(blocks)).futureValue
      BlockDao.updateLatestBlock(blocks.last).futureValue
    }
  }

  private def makeBlock(
      chainIndex: ChainIndex,
      height: Int,
      timestamp: TimeStamp
  ): BlockEntity = {
    val hash = BlockHash.unsafe(ByteString.fromArrayUnsafe(Array.fill[Byte](32)(height.toByte)))

    BlockEntity(
      hash = hash,
      timestamp = timestamp,
      chainFrom = chainIndex.from,
      chainTo = chainIndex.to,
      height = Height.unsafe(height),
      deps = ArraySeq.fill(groupSetting.groupNum)(hash),
      transactions = ArraySeq.empty,
      inputs = ArraySeq.empty,
      outputs = ArraySeq.empty,
      mainChain = true,
      nonce = ByteString.fromArrayUnsafe(Array.fill[Byte](32)((height + 1).toByte)),
      version = 1,
      depStateHash =
        Hash.unsafe(ByteString.fromArrayUnsafe(Array.fill[Byte](32)((height + 2).toByte))),
      txsHash = Hash.unsafe(ByteString.fromArrayUnsafe(Array.fill[Byte](32)((height + 3).toByte))),
      target = ByteString.fromArrayUnsafe(Array.fill[Byte](32)((height + 4).toByte)),
      hashrate = BigInteger.valueOf(height.toLong),
      ghostUncles = ArraySeq.empty,
      conflictedTxs = None
    )
  }
}
