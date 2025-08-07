// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import sttp.model.StatusCode

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer._
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.persistence.{DatabaseFixtureForAll, DBRunner}
import org.alephium.explorer.service.{EmptyTokenService, EmptyTransactionService}
import org.alephium.util.{TimeStamp, U256}

class StatementTimeoutSpec()
    extends AlephiumFutureSpec
    with DatabaseFixtureForAll
    with HttpServerFixture
    with DBRunner {

  implicit val blockCache: BlockCache = TestBlockCache()

  val server =
    new AddressServer(
      StatementTimeoutSpec.transactionService(),
      new EmptyTokenService {},
      exportTxsNumberThreshold = 1000,
      streamParallelism = 8,
      maxTimeInterval = ConfigDefaults.maxTimeIntervals.amountHistory,
      maxTimeIntervalExportTxs = ConfigDefaults.maxTimeIntervals.exportTxs
    )

  val routes = server.routes

  "long query return a 504" in {
    forAll(addressGen) { case address =>
      Get(s"/addresses/${address}/balance") check { response =>
        response.code is StatusCode.GatewayTimeout
      }
    }
  }
}

object StatementTimeoutSpec {
  def transactionService() = new EmptyTransactionService {
    override def getBalance(
        address: ApiAddress,
        latestFinalizedBlock: TimeStamp
    )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
      DBRunner
        .run((for {
          _   <- sqlu"SET LOCAL statement_timeout TO '1'"
          res <- sql"SELECT pg_sleep(0.05), count(*) FROM block_headers".as[Int]
        } yield res).transactionally)
        .map(_ => (U256.Zero, U256.Zero))

  }
}
