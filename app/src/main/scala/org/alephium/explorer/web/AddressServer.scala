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
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.api.AddressesEndpoints
import org.alephium.explorer.api.model.{AddressBalance, AddressInfo}
import org.alephium.explorer.service.TransactionService

class AddressServer(transactionService: TransactionService)(implicit ec: ExecutionContext,
                                                            dc: DatabaseConfig[PostgresProfile])
    extends Server
    with AddressesEndpoints {

  val route: Route =
    toRoute(getTransactionsByAddress) {
      case (address, pagination) =>
        transactionService
          .getTransactionsByAddressSQL(address, pagination)
          .map(Right.apply)
    } ~
      toRoute(getAddressInfo) { address =>
        for {
          (balance, locked) <- transactionService.getBalance(address)
          txNumber          <- transactionService.getTransactionsNumberByAddress(address)
        } yield Right(AddressInfo(balance, locked, txNumber))
      } ~
      toRoute(getTotalTransactionsByAddress) { address =>
        transactionService.getTransactionsNumberByAddress(address).map(Right(_))
      } ~
      toRoute(getAddressBalance) { address =>
        for {
          (balance, locked) <- transactionService.getBalance(address)
        } yield Right(AddressBalance(balance, locked))
      } ~
      toRoute(getAddressTokenBalance) {
        case (address, token) =>
          for {
            (balance, locked) <- transactionService.getTokenBalance(address, token)
          } yield Right(AddressBalance(balance, locked))
      } ~
      toRoute(listAddressTokens) { address =>
        for {
          tokens <- transactionService.listAddressTokens(address)
        } yield Right(tokens)
      } ~
      toRoute(listAddressTokenTransactions) {
        case (address, token, pagination) =>
          for {
            tokens <- transactionService.listAddressTokenTransactions(address, token, pagination)
          } yield Right(tokens)
      }

}
