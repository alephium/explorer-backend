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

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import org.scalacheck.Gen
import sttp.model.Uri

import org.alephium.api.model.{ChainInfo, ChainParams, HashesAtHeight, SelfClique}
import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenApiModel.utransactionGen
import org.alephium.explorer.api.model.{GroupIndex, Height, UnconfirmedTransaction}
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.persistence.dao.UnconfirmedTxDao
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.util.Scheduler
import org.alephium.explorer.util.TestUtils._
import org.alephium.protocol.model.BlockHash
import org.alephium.util.{Service, TimeStamp}

class MempoolSyncServiceSpec extends AlephiumFutureSpec with DatabaseFixtureForEach {

  "start/sync/stop" in new Fixture {
    using(Scheduler("test")) { implicit scheduler =>
      MempoolSyncService.start(ArraySeq(Uri("")), 100.milliseconds)

      UnconfirmedTxDao.listHashes().futureValue is ArraySeq.empty

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
    }
  }

  trait Fixture {
    implicit val executionContext: ExecutionContext = ExecutionContext.global

    var unconfirmedTransactions: ArraySeq[UnconfirmedTransaction] = ArraySeq.empty

    implicit val blockFlowClient: BlockFlowClient = new BlockFlowClient {
      implicit val executionContext: ExecutionContext = ExecutionContext.global
      def startSelfOnce(): Future[Unit]               = Future.unit
      def stopSelfOnce(): Future[Unit]                = Future.unit
      def subServices: ArraySeq[Service]              = ArraySeq.empty
      def fetchUnconfirmedTransactions(uri: Uri): Future[ArraySeq[UnconfirmedTransaction]] =
        Future.successful(unconfirmedTransactions)
      def fetchBlock(from: GroupIndex, hash: BlockHash): Future[BlockEntity] =
        ???
      def fetchBlocks(fromTs: TimeStamp,
                      toTs: TimeStamp,
                      uri: Uri): Future[ArraySeq[ArraySeq[BlockEntity]]] =
        ???
      def fetchChainInfo(from: GroupIndex, to: GroupIndex): Future[ChainInfo] = ???
      def fetchHashesAtHeight(from: GroupIndex,
                              to: GroupIndex,
                              height: Height): Future[HashesAtHeight] = ???
      def fetchSelfClique(): Future[SelfClique]                       = ???
      def fetchChainParams(): Future[ChainParams]                     = ???
      override def start(): Future[Unit]                              = ???
      override def close(): Future[Unit]                              = ???
    }

  }
}
