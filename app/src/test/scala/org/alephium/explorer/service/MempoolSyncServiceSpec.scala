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

import akka.http.scaladsl.model.Uri
import org.scalacheck.Gen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}

import org.alephium.api.model.{ChainInfo, ChainParams, HashesAtHeight, SelfClique}
import org.alephium.explorer.{AlephiumSpec, Generators}
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height, UnconfirmedTransaction}
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.persistence.dao.UnconfirmedTxDao
import org.alephium.explorer.persistence.model._
import org.alephium.util.{Duration, TimeStamp}

class MempoolSyncServiceSpec
    extends AlephiumSpec
    with DatabaseFixtureForEach
    with Generators
    with ScalaFutures
    with Eventually {
  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Minutes))

  it should "start/sync/stop" in new Fixture {
    val mempoolSyncService =
      MempoolSyncService(syncPeriod = Duration.unsafe(100), blockFlowClient, UnconfirmedTxDao)

    mempoolSyncService.start(Seq("")).futureValue is ()

    UnconfirmedTxDao.listHashes().futureValue is Seq.empty

    unconfirmedTransactions = Gen.listOfN(10, utransactionGen).sample.get

    eventually {
      UnconfirmedTxDao.listHashes().futureValue.toSet is unconfirmedTransactions.map(_.hash).toSet
    }

    val head   = unconfirmedTransactions.head
    val last   = unconfirmedTransactions.last
    val middle = unconfirmedTransactions(5)

    val newUnconfirmedTransactions =
      unconfirmedTransactions.filterNot(tx => tx == head || tx == last || tx == middle)

    unconfirmedTransactions = newUnconfirmedTransactions

    eventually {
      UnconfirmedTxDao.listHashes().futureValue.toSet is newUnconfirmedTransactions
        .map(_.hash)
        .toSet
    }

    mempoolSyncService.stop().futureValue is ()

    databaseConfig.db.close
  }

  trait Fixture {
    implicit val executionContext: ExecutionContext = ExecutionContext.global

    var unconfirmedTransactions: Seq[UnconfirmedTransaction] = Seq.empty

    val blockFlowClient: BlockFlowClient = new BlockFlowClient {
      def fetchUnconfirmedTransactions(
          uri: Uri): Future[Either[String, Seq[UnconfirmedTransaction]]] =
        Future.successful(Right(unconfirmedTransactions))
      def fetchBlock(from: GroupIndex, hash: BlockEntry.Hash): Future[Either[String, BlockEntity]] =
        ???
      def fetchBlocks(fromTs: TimeStamp,
                      toTs: TimeStamp,
                      uri: Uri): Future[Either[String, Seq[Seq[BlockEntity]]]]                = ???
      def fetchChainInfo(from: GroupIndex, to: GroupIndex): Future[Either[String, ChainInfo]] = ???
      def fetchHashesAtHeight(from: GroupIndex,
                              to: GroupIndex,
                              height: Height): Future[Either[String, HashesAtHeight]] = ???
      def fetchSelfClique(): Future[Either[String, SelfClique]]                       = ???
      def fetchChainParams(): Future[Either[String, ChainParams]]                     = ???
    }

  }
}
