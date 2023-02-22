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

import akka.actor.ActorSystem
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import io.vertx.ext.reactivestreams.ReactiveReadStream
import io.vertx.ext.web._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.StatusCode

import org.alephium.api.ApiError
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.AddressesEndpoints
import org.alephium.explorer.api.model._
import org.alephium.explorer.service.TransactionService
import org.alephium.protocol.model.Address

class AddressServer(transactionService: TransactionService, exportTxsNumberThreshold: Int)(
    implicit val executionContext: ExecutionContext,
    ac: ActorSystem,
    groupSetting: GroupSetting,
    dc: DatabaseConfig[PostgresProfile])
    extends Server
    with AddressesEndpoints {

  val groupNum = groupSetting.groupNum

  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      route(getTransactionsByAddress.serverLogicSuccess[Future] {
        case (address, pagination) =>
          transactionService
            .getTransactionsByAddress(address, pagination)
      }),
      route(getTransactionsByAddresses.serverLogicSuccess[Future] {
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
      route(addressMempoolTransactions.serverLogicSuccess[Future] { address =>
        transactionService
          .listMempoolTransactionsByAddress(address)
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
      route(areAddressesActive.serverLogicSuccess[Future] { addresses =>
        transactionService.areAddressesActive(addresses)
      }),
      route(exportTransactionsCsvByAddress.serverLogic[Future] {
        case (address, timeInterval) =>
          exportTransactions(address, timeInterval, ExportType.CSV).map(_.map { stream =>
            (AddressServer.exportFileNameHeader(address, timeInterval), stream)
          })
      })
    )

  private def exportTransactions(
      address: Address,
      timeInterval: TimeInterval,
      exportType: ExportType): Future[Either[ApiError[_ <: StatusCode], ReadStream[Buffer]]] = {
    transactionService
      .hasAddressMoreTxsThan(address, timeInterval.from, timeInterval.to, exportTxsNumberThreshold)
      .map { hasMore =>
        if (hasMore) {
          Left(ApiError.BadRequest(
            s"Too many transactions for that address in this time range, limit is $exportTxsNumberThreshold"))
        } else {
          val readStream: ReactiveReadStream[Buffer] = ReactiveReadStream.readStream();
          val pub = transactionService.exportTransactionsByAddress(address,
                                                                   timeInterval.from,
                                                                   timeInterval.to,
                                                                   exportType,
                                                                   1)
          pub.subscribe(readStream)
          Right(readStream)
        }
      }
  }
}

object AddressServer {
  def exportFileNameHeader(address: Address, timeInterval: TimeInterval): String = {
    s"""attachment;filename="$address-${timeInterval.from.millis}-${timeInterval.to.millis}.csv""""
  }
}
