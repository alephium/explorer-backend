// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future
import scala.concurrent.duration._

import org.scalacheck.Gen
import sttp.model.Uri

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenApiModel.mempooltransactionGen
import org.alephium.explorer.api.model.MempoolTransaction
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.persistence.dao.MempoolDao
import org.alephium.explorer.util.Scheduler
import org.alephium.explorer.util.TestUtils._

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

    implicit val blockFlowClient: BlockFlowClient = new EmptyBlockFlowClient {
      override def fetchMempoolTransactions(uri: Uri): Future[ArraySeq[MempoolTransaction]] =
        Future.successful(mempoolTransactions)
    }
  }
}
