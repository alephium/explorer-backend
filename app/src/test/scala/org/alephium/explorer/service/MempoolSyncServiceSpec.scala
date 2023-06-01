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
import org.alephium.explorer.GenApiModel.mempooltransactionGen
import org.alephium.explorer.api.model.{Height, MempoolTransaction}
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.persistence.dao.MempoolDao
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.util.Scheduler
import org.alephium.explorer.util.TestUtils._
import org.alephium.protocol.model.{BlockHash, ChainIndex, GroupIndex}
import org.alephium.util.{Service, TimeStamp}

class MempoolSyncServiceSpec extends AlephiumFutureSpec with DatabaseFixtureForEach {

  "start/sync/stop" in new Fixture {
    using(Scheduler("test")) { implicit scheduler =>
      MempoolSyncService.start(ArraySeq(Uri("")), 100.milliseconds)

      MempoolDao.listHashes().futureValue is ArraySeq.empty

      mempoolTransactions = Gen.listOfN(10, mempooltransactionGen).sample.get

      eventually {
        MempoolDao.listHashes().futureValue.toSet is mempoolTransactions.map(_.hash).toSet
      }

      val head   = mempoolTransactions.head
      val last   = mempoolTransactions.last
      val middle = mempoolTransactions(5)

      val newMempoolTransactions =
        mempoolTransactions.filterNot(tx => tx == head || tx == last || tx == middle)

      mempoolTransactions = newMempoolTransactions

      eventually {
        MempoolDao.listHashes().futureValue.toSet is newMempoolTransactions
          .map(_.hash)
          .toSet
      }
    }
  }

  trait Fixture {
    var mempoolTransactions: ArraySeq[MempoolTransaction] = ArraySeq.empty

    implicit val blockFlowClient: BlockFlowClient = new BlockFlowClient {
      implicit val executionContext: ExecutionContext = implicitly
      def startSelfOnce(): Future[Unit]               = Future.unit
      def stopSelfOnce(): Future[Unit]                = Future.unit
      def subServices: ArraySeq[Service]              = ArraySeq.empty
      def fetchMempoolTransactions(uri: Uri): Future[ArraySeq[MempoolTransaction]] =
        Future.successful(mempoolTransactions)
      def fetchBlock(from: GroupIndex, hash: BlockHash): Future[BlockEntity] =
        ???
      def fetchBlockAndEvents(fromGroup: GroupIndex,
                              hash: BlockHash): Future[BlockEntityWithEvents] =
        ???
      def fetchBlocks(fromTs: TimeStamp,
                      toTs: TimeStamp,
                      uri: Uri): Future[ArraySeq[ArraySeq[BlockEntityWithEvents]]] =
        ???
      def fetchChainInfo(chainIndex: ChainIndex): Future[ChainInfo]                           = ???
      def fetchHashesAtHeight(chainIndex: ChainIndex, height: Height): Future[HashesAtHeight] = ???
      def fetchSelfClique(): Future[SelfClique]                                               = ???
      def fetchChainParams(): Future[ChainParams]                                             = ???
      override def start(): Future[Unit]                                                      = ???
      override def close(): Future[Unit]                                                      = ???
    }

  }
}
