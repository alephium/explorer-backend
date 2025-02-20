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

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import sttp.model.StatusCode

import org.alephium.explorer._
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.persistence.{DatabaseFixtureForAll, DBRunner}
import org.alephium.explorer.service.{EmptyTokenService, EmptyTransactionService}
import org.alephium.protocol.model.{Address, GroupIndex}
import org.alephium.util.{TimeStamp, U256}

class StatementTimeoutSpec()
    extends AlephiumActorSpecLike
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
        address: Address,
        groupIndex: Option[GroupIndex],
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
