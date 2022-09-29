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

import io.vertx.ext.web._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.ApiError
import org.alephium.explorer.api.TransactionEndpoints
import org.alephium.explorer.service.TransactionService

class TransactionServer(implicit val executionContext: ExecutionContext,
                        dc: DatabaseConfig[PostgresProfile])
    extends Server
    with TransactionEndpoints {
  val routes: ArraySeq[Router => Route] = ArraySeq(
    route(getTransactionById.serverLogic[Future] { hash =>
      TransactionService
        .getTransaction(hash)
        .map(_.toRight(ApiError.NotFound(hash.value.toHexString)))
    }),
    route(getOutputRefTransaction.serverLogic[Future] { hash =>
      TransactionService
        .getOutputRefTransaction(hash)
        .map(_.toRight(ApiError.NotFound(hash.toHexString)))
    })
  )

}
