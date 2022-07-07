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

import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpupickle.UpickleCustomizationSupport
import org.scalatest.concurrent.ScalaFutures
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.{AlephiumSpec, BuildInfo, Generators, GroupSetting, Hash}
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.{BlockCache, TransactionCache}
import org.alephium.explorer.persistence.{Database, DatabaseFixtureForEach}
import org.alephium.explorer.service._
import org.alephium.json.Json
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp, U256}

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class InfosServerSpec()
    extends AlephiumSpec
    with AkkaDecodeFailureHandler
    with DatabaseFixtureForEach
    with ScalaFutures
    with ScalatestRouteTest
    with UpickleCustomizationSupport {
  override type Api = Json.type

  override def api: Api = Json

  "return the explorer infos" in new Fixture {
    Get(s"/infos") ~> server.route ~> check {
      responseAs[ExplorerInfo] is ExplorerInfo(
        BuildInfo.releaseVersion,
        BuildInfo.commitId
      )
    }
  }

  "return chains heights" in new Fixture {
    Get(s"/infos/heights") ~> server.route ~> check {
      responseAs[Seq[PerChainHeight]] is Seq(chainHeight)
    }
  }

  "return the token supply list" in new Fixture {
    Get(s"/infos/supply") ~> server.route ~> check {
      responseAs[Seq[TokenSupply]] is Seq(tokenSupply)
    }
  }

  "return the token current supply" in new Fixture {
    Get(s"/infos/supply/circulating-alph") ~> server.route ~> check {
      val circulating = response.entity
        .toStrict(Duration.ofSecondsUnsafe(5).asScala)
        .map(_.data.utf8String)
        .futureValue

      circulating is "2"
    }
  }

  "return the total token supply" in new Fixture {
    Get(s"/infos/supply/total-alph") ~> server.route ~> check {
      val total = response.entity
        .toStrict(Duration.ofSecondsUnsafe(5).asScala)
        .map(_.data.utf8String)
        .futureValue

      total is "1"
    }
  }

  "return the reserved token supply" in new Fixture {
    Get(s"/infos/supply/reserved-alph") ~> server.route ~> check {
      val reserved = response.entity
        .toStrict(Duration.ofSecondsUnsafe(5).asScala)
        .map(_.data.utf8String)
        .futureValue

      reserved is "3"
    }
  }

  "return the locked token supply" in new Fixture {
    Get(s"/infos/supply/locked-alph") ~> server.route ~> check {
      val locked = response.entity
        .toStrict(Duration.ofSecondsUnsafe(5).asScala)
        .map(_.data.utf8String)
        .futureValue

      locked is "4"
    }
  }

  "return the total transactions number" in new Fixture {
    Get(s"/infos/total-transactions") ~> server.route ~> check {
      val total = response.entity
        .toStrict(Duration.ofSecondsUnsafe(5).asScala)
        .map(_.data.utf8String)
        .futureValue

      total is "10"
    }
  }

  "return the average block times" in new Fixture {
    Get(s"/infos/average-block-times") ~> server.route ~> check {
      responseAs[Seq[PerChainDuration]] is Seq(blockTime)
    }
  }
  trait Fixture extends Generators {
    val tokenSupply = TokenSupply(TimeStamp.zero,
                                  ALPH.alph(1),
                                  ALPH.alph(2),
                                  ALPH.alph(3),
                                  ALPH.alph(4),
                                  ALPH.alph(5))
    val tokenSupplyService = new TokenSupplyService {
      def listTokenSupply(pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[TokenSupply]] =
        Future.successful(
          Seq(
            tokenSupply
          ))

      def getLatestTokenSupply()(implicit ec: ExecutionContext,
                                 dc: DatabaseConfig[PostgresProfile]): Future[Option[TokenSupply]] =
        Future.successful(
          Some(
            tokenSupply
          ))

    }

    val chainHeight = PerChainHeight(0, 0, 60000, 60000)
    val blockTime   = PerChainDuration(0, 0, 1, 1)
    val blockService = new BlockService {

      def getLiteBlockByHash(hash: BlockEntry.Hash)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Option[BlockEntryLite]] =
        ???

      def getBlockTransactions(hash: BlockEntry.Hash, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
        ???

      def listBlocks(pagination: Pagination)(implicit ec: ExecutionContext,
                                             dc: DatabaseConfig[PostgresProfile],
                                             cache: BlockCache): Future[ListBlocks] =
        ???

      def listMaxHeights()(implicit cache: BlockCache,
                           groupSetting: GroupSetting,
                           ec: ExecutionContext): Future[Seq[PerChainHeight]] =
        Future.successful(Seq(chainHeight))

      def getAverageBlockTime()(implicit cache: BlockCache,
                                groupSetting: GroupSetting,
                                ec: ExecutionContext): Future[Seq[PerChainDuration]] =
        Future.successful(Seq(blockTime))

    }

    val transactionService = new TransactionService {
      override def getTransaction(transactionHash: Transaction.Hash)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Option[TransactionLike]] =
        Future.successful(None)

      override def getOutputRefTransaction(key: Hash)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Option[ConfirmedTransaction]] =
        Future.successful(None)

      override def getTransactionsNumberByAddress(address: Address)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Int] =
        Future.successful(0)

      override def getTransactionsByAddressSQL(address: Address, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
        Future.successful(Seq.empty)

      override def getTransactionsByAddress(address: Address, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
        Future.successful(Seq.empty)

      override def getBalance(address: Address)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
        Future.successful((U256.Zero, U256.Zero))

      def getTotalNumber()(implicit cache: TransactionCache): Int = 10

      def getTokenBalance(address: Address, token: Hash)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] = ???
      def listAddressTokenTransactions(address: Address, token: Hash, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] = ???
      def listAddressTokens(address: Address)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Hash]] = ???
      def listTokenAddresses(token: Hash, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Address]] = ???
      def listTokenTransactions(token: Hash, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] = ???
      def listTokens(pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Hash]] = ???
      def areAddressesActive(addresses: Seq[Address])(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Boolean]] =
        ???
    }

    implicit val groupSettings: GroupSetting        = GroupSetting(groupNum)
    implicit val blockCache: BlockCache             = BlockCache()
    implicit val transactionCache: TransactionCache = TransactionCache(new Database(false))

    val server =
      new InfosServer(tokenSupplyService, blockService, transactionService)
  }
}
