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

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import sttp.tapir.server.akkahttp.{AkkaHttpServerOptions, RichAkkaHttpEndpoint}

import org.alephium.explorer.api.AddressesEndpoints
import org.alephium.explorer.api.model.AddressInfo
import org.alephium.explorer.service.TransactionService

class AddressServer(transactionService: TransactionService)(
    implicit val serverOptions: AkkaHttpServerOptions,
    executionContext: ExecutionContext)
    extends Server
    with AddressesEndpoints {
  val route: Route =
    getTransactionsByAddress.toRoute(address =>
      transactionService.getTransactionsByAddress(address).map(Right.apply)) ~
      getAddressInfo.toRoute(address =>
        for {
          balance      <- transactionService.getBalance(address)
          transactions <- transactionService.getTransactionsByAddress(address)
        } yield Right(AddressInfo(balance, transactions)))
}
