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

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.AddressesEndpoints._
import org.alephium.explorer.api.model.{AddressBalance, AddressInfo}
import org.alephium.explorer.service.TransactionService

class AddressServer(transactionService: TransactionService)(
    implicit val executionContext: ExecutionContext,
    groupSetting: GroupSetting,
    dc: DatabaseConfig[PostgresProfile])
    extends Server {

  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      route(getTransactionsByAddress.serverLogicSuccess[Future] {
        case (address, pagination) =>
          transactionService
            .getTransactionsByAddress(address, pagination)
      }),
      route(getTransactionsByAddresses(groupSetting.groupNum).serverLogicSuccess[Future] {
        case (addresses, pagination) =>
          transactionService
            .getTransactionsByAddresses(addresses, pagination)
      }),
      route(getTransactionsByAddressDEPRECATED.serverLogicSuccess[Future] {
        case (address, pagination) =>
          transactionService
            .getTransactionsByAddressSQL(address, pagination)
      }),
      route(getTransactionsByAddressTimeRanged.serverLogicSuccess[Future] {
        case (address, timeInterval, pagination) =>
          transactionService
            .getTransactionsByAddressTimeRangedSQL(address,
                                                   timeInterval.from,
                                                   timeInterval.to,
                                                   pagination)
      }),
      route(addressUnconfirmedTransactions.serverLogicSuccess[Future] { address =>
        transactionService
          .listUnconfirmedTransactionsByAddress(address)
      }),
      route(getAddressInfo.serverLogicSuccess[Future] { address =>
        for {
          (balance, locked) <- transactionService.getBalance(address)
          txNumber          <- transactionService.getTransactionsNumberByAddress(address)
        } yield AddressInfo(balance, locked, txNumber)
      }),
      route(getTotalTransactionsByAddress.serverLogic[Future] { address =>
        transactionService.getTransactionsNumberByAddress(address).map(Right(_))
      }),
      route(getAddressBalance.serverLogicSuccess[Future] { address =>
        for {
          (balance, locked) <- transactionService.getBalance(address)
        } yield AddressBalance(balance, locked)
      }),
      route(getAddressTokenBalance.serverLogicSuccess[Future] {
        case (address, token) =>
          for {
            (balance, locked) <- transactionService.getTokenBalance(address, token)
          } yield AddressBalance(balance, locked)
      }),
      route(listAddressTokens.serverLogicSuccess[Future] { address =>
        for {
          tokens <- transactionService.listAddressTokens(address)
        } yield tokens
      }),
      route(listAddressTokenTransactions.serverLogicSuccess[Future] {
        case (address, token, pagination) =>
          for {
            tokens <- transactionService.listAddressTokenTransactions(address, token, pagination)
          } yield tokens
      }),
      route(areAddressesActive(groupSetting.groupNum).serverLogicSuccess[Future] { addresses =>
        transactionService.areAddressesActive(addresses)
      })
    )

}
