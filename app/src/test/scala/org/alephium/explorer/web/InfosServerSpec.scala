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

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer._
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache, TransactionCache}
import org.alephium.explorer.config.BootMode
import org.alephium.explorer.persistence.{Database, DatabaseFixtureForAll, Migrations}
import org.alephium.explorer.service._
import org.alephium.protocol.ALPH
import org.alephium.util.TimeStamp

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class InfosServerSpec()
    extends AlephiumActorSpecLike
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

  val chainHeight = PerChainHeight(0, 0, 60000, 60000)
  val blockTime   = PerChainDuration(0, 0, 1, 1)
  val blockService = new EmptyBlockService {

    override def listMaxHeights()(implicit
        cache: BlockCache,
        groupSetting: GroupSetting,
        ec: ExecutionContext
    ): Future[ArraySeq[PerChainHeight]] =
      Future.successful(ArraySeq(chainHeight))

    override def getAverageBlockTime()(implicit
        cache: BlockCache,
        groupSetting: GroupSetting,
        ec: ExecutionContext
    ): Future[ArraySeq[PerChainDuration]] =
      Future.successful(ArraySeq(blockTime))
  }
  implicit val blockCache: BlockCache = TestBlockCache()
  implicit val transactionCache: TransactionCache = TransactionCache(
    new Database(BootMode.ReadWrite)
  )
  val transactionService = new EmptyTransactionService {
    override def getTotalNumber()(implicit cache: TransactionCache): Int = 10
  }

  val infoServer =
    new InfosServer(tokenSupplyService, blockService, transactionService, servicesConfig)

  val routes = infoServer.routes

  "return the explorer infos" in {
    Get(s"/infos") check { response =>
      response.as[ExplorerInfo] is ExplorerInfo(
        BuildInfo.releaseVersion,
        BuildInfo.commitId,
        Migrations.latestVersion.version,
        TimeStamp.zero
      )
    }
  }

  "return chains heights" in {
    Get(s"/infos/heights") check { response =>
      response.as[ArraySeq[PerChainHeight]] is ArraySeq(chainHeight)
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

  "return the average block times" in {
    Get(s"/infos/average-block-times") check { response =>
      response.as[ArraySeq[PerChainDuration]] is ArraySeq(blockTime)
    }
  }
}
