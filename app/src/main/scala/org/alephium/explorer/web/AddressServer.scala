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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.AddressesEndpoints
import org.alephium.explorer.api.model.{AddressBalance, AddressInfo}
import org.alephium.explorer.service.TransactionService

class AddressServer(transactionService: TransactionService)(
    implicit val executionContext: ExecutionContext,
    groupSetting: GroupSetting,
    dc: DatabaseConfig[PostgresProfile])
    extends Server
    with AddressesEndpoints {

  val groupNum = groupSetting.groupNum

  val route: Route =
    toRoute(getTransactionsByAddress.serverLogicSuccess[Future] {
      case (address, pagination) =>
        transactionService
          .getTransactionsByAddress(address, pagination)
    }) ~
      toRoute(getTransactionsByAddressDEPRECATED.serverLogicSuccess[Future] {
        case (address, pagination) =>
          transactionService
            .getTransactionsByAddressSQL(address, pagination)
      }) ~
      toRoute(addressUnconfirmedTransactions.serverLogicSuccess[Future] { address =>
        transactionService
          .listUnconfirmedTransactionsByAddress(address)
      }) ~
      toRoute(getAddressInfo.serverLogicSuccess[Future] { address =>
        for {
          (balance, locked) <- transactionService.getBalance(address)
          txNumber          <- transactionService.getTransactionsNumberByAddress(address)
        } yield AddressInfo(balance, locked, txNumber)
      }) ~
      toRoute(getTotalTransactionsByAddress.serverLogic[Future] { address =>
        transactionService.getTransactionsNumberByAddress(address).map(Right(_))
      }) ~
      toRoute(getAddressBalance.serverLogicSuccess[Future] { address =>
        for {
          (balance, locked) <- transactionService.getBalance(address)
        } yield AddressBalance(balance, locked)
      }) ~
      toRoute(getAddressTokenBalance.serverLogicSuccess[Future] {
        case (address, token) =>
          for {
            (balance, locked) <- transactionService.getTokenBalance(address, token)
          } yield AddressBalance(balance, locked)
      }) ~
      toRoute(listAddressTokens.serverLogicSuccess[Future] { address =>
        for {
          tokens <- transactionService.listAddressTokens(address)
        } yield tokens
      }) ~
      toRoute(listAddressTokenTransactions.serverLogicSuccess[Future] {
        case (address, token, pagination) =>
          for {
            tokens <- transactionService.listAddressTokenTransactions(address, token, pagination)
          } yield tokens
      }) ~
      toRoute(areAddressesActive.serverLogicSuccess[Future] { addresses =>
        transactionService.areAddressesActive(addresses)
      })

}
