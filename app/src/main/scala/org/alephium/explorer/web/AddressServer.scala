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

import akka.util.ByteString
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import io.vertx.ext.web._
import io.vertx.rxjava3.FlowableHelper
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.StatusCode

import org.alephium.api.ApiError
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.AddressesEndpoints
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.service.{TokenService, TransactionService}
import org.alephium.protocol.PublicKey
import org.alephium.protocol.model.AddressLike
import org.alephium.protocol.vm.{LockupScript, PublicKeyLike, UnlockScript}
import org.alephium.serde._
import org.alephium.util.Duration

class AddressServer(
    transactionService: TransactionService,
    tokenService: TokenService,
    exportTxsNumberThreshold: Int,
    streamParallelism: Int,
    maxTimeInterval: ExplorerConfig.MaxTimeInterval,
    val maxTimeIntervalExportTxs: Duration
)(implicit
    val executionContext: ExecutionContext,
    groupSetting: GroupSetting,
    blockCache: BlockCache,
    dc: DatabaseConfig[PostgresProfile]
) extends Server
    with AddressesEndpoints {

  val groupNum = groupSetting.groupNum

  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      route(getTransactionsByAddress.serverLogicSuccess[Future] { case (address, pagination) =>
        transactionService
          .getTransactionsByAddress(address, pagination)
      }),
      route(getTransactionsByAddresses.serverLogicSuccess[Future] {
        case (addresses, fromTsOpt, toTsOpt, pagination) =>
          transactionService
            .getTransactionsByAddresses(addresses, fromTsOpt, toTsOpt, pagination)
      }),
      route(getTransactionsByAddressTimeRanged.serverLogicSuccess[Future] {
        case (address, timeInterval, pagination) =>
          transactionService
            .getTransactionsByAddressTimeRanged(
              address,
              timeInterval.from,
              timeInterval.to,
              pagination
            )
      }),
      route(addressMempoolTransactions.serverLogicSuccess[Future] { address =>
        transactionService
          .listMempoolTransactionsByAddress(address)
      }),
      route(getAddressInfo.serverLogicSuccess[Future] { address =>
        for {
          (balance, locked) <- transactionService
            .getBalance(address, blockCache.getLastFinalizedTimestamp())
          txNumber <- transactionService.getTransactionsNumberByAddress(address)
        } yield AddressInfo(balance, locked, txNumber)
      }),
      route(getTotalTransactionsByAddress.serverLogic[Future] { address =>
        transactionService.getTransactionsNumberByAddress(address).map(Right(_))
      }),
      route(getLatestTransactionInfo.serverLogic[Future] { address =>
        transactionService.getLatestTransactionInfoByAddress(address).map {
          case None         => Left(ApiError.NotFound(s"No transaction found for address $address"))
          case Some(txInfo) => Right(txInfo)
        }
      }),
      route(getAddressBalance.serverLogicSuccess[Future] { case address =>
        for {
          (balance, locked) <- transactionService
            .getBalance(address, blockCache.getLastFinalizedTimestamp())
        } yield AddressBalance(balance, locked)
      }),
      route(getAddressTokenBalance.serverLogicSuccess[Future] { case (address, token) =>
        for {
          (balance, locked) <- tokenService.getTokenBalance(address, token)
        } yield AddressTokenBalance(token, balance, locked)
      }),
      route(listAddressTokensBalance.serverLogicSuccess[Future] { case (address, pagination) =>
        tokenService
          .listAddressTokensWithBalance(address, pagination)
          .map(_.map { case (tokenId, balance, locked) =>
            AddressTokenBalance(tokenId, balance, locked)
          })
      }),
      route(listAddressTokens.serverLogicSuccess[Future] { case (address, pagination) =>
        for {
          tokens <- tokenService.listAddressTokens(address, pagination)
        } yield tokens
      }),
      route(listAddressTokenTransactions.serverLogicSuccess[Future] {
        case (address, token, pagination) =>
          for {
            tokens <- tokenService.listAddressTokenTransactions(address, token, pagination)
          } yield tokens
      }),
      route(areAddressesActive.serverLogicSuccess[Future] { addresses =>
        transactionService.areAddressesActive(addresses)
      }),
      route(exportTransactionsCsvByAddress.serverLogic[Future] { case (address, timeInterval) =>
        exportTransactions(address, timeInterval).map(_.map { stream =>
          (AddressServer.exportFileNameHeader(address, timeInterval), stream)
        })
      }),
      route(getAddressAmountHistoryDEPRECATED.serverLogic[Future] {
        case (address, timeInterval, intervalType) =>
          validateTimeInterval(timeInterval, intervalType) {
            val flowable =
              transactionService.getAmountHistoryDEPRECATED(
                address,
                timeInterval.from,
                timeInterval.to,
                intervalType,
                streamParallelism
              )
            Future.successful(
              (
                AddressServer.amountHistoryFileNameHeader(address, timeInterval),
                FlowableHelper.toReadStream(flowable)
              )
            )
          }
      }),
      route(getAddressAmountHistory.serverLogic[Future] {
        case (address, timeInterval, intervalType) =>
          validateTimeInterval(timeInterval, intervalType) {
            transactionService
              .getAmountHistory(
                address,
                timeInterval.from,
                timeInterval.to,
                intervalType
              )
              .map { values =>
                AmountHistory(values.map { case (ts, value) =>
                  TimedAmount(ts, value)
                })
              }
          }
      }),
      route(getPublicKey.serverLogic[Future] { address =>
        address.lockupScriptResult match {
          case LockupScript.CompleteLockupScript(LockupScript.P2PKH(_)) =>
            transactionService.getUnlockScript(address).map {
              case None =>
                Left(ApiError.NotFound(s"No input found for address $address"))
              case Some(unlockScript) =>
                AddressServer.bytesToPublicKey(unlockScript)
            }
          case LockupScript.CompleteLockupScript(
                LockupScript.P2PK(PublicKeyLike.SecP256K1(publicKey), _)
              ) =>
            Future.successful(Right(publicKey))
          case _ =>
            Future.successful(
              Left(ApiError.BadRequest(s"Only P2PKH and P2PK (SecP256K1) addresses supported"))
            )
        }
      })
    )

  private def exportTransactions(
      address: AddressLike,
      timeInterval: TimeInterval
  ): Future[Either[ApiError[_ <: StatusCode], ReadStream[Buffer]]] = {
    transactionService
      .hasAddressMoreTxsThan(address, timeInterval.from, timeInterval.to, exportTxsNumberThreshold)
      .map { hasMore =>
        if (hasMore) {
          Left(
            ApiError.BadRequest(
              s"Too many transactions for that address in this time range, limit is $exportTxsNumberThreshold"
            )
          )
        } else {
          val flowable = transactionService.exportTransactionsByAddress(
            address,
            timeInterval.from,
            timeInterval.to,
            1,
            streamParallelism
          )
          Right(FlowableHelper.toReadStream(flowable))
        }
      }
  }

  private def validateTimeInterval[A](timeInterval: TimeInterval, intervalType: IntervalType)(
      contd: => Future[A]
  ): Future[Either[ApiError[_ <: StatusCode], A]] =
    IntervalType.validateTimeInterval(
      timeInterval,
      intervalType,
      maxTimeInterval.hourly,
      maxTimeInterval.daily,
      maxTimeInterval.weekly
    )(contd)
}

object AddressServer {
  def exportFileNameHeader(address: AddressLike, timeInterval: TimeInterval): String = {
    s"""attachment;filename="$address-${timeInterval.from.millis}-${timeInterval.to.millis}.csv""""
  }

  def amountHistoryFileNameHeader(address: AddressLike, timeInterval: TimeInterval): String = {
    s"""attachment;filename="$address-amount-history-${timeInterval.from.millis}-${timeInterval.to.millis}.json""""
  }

  def bytesToPublicKey(
      unlockScript: ByteString
  ): Either[ApiError[_ <: StatusCode], PublicKey] =
    deserialize[UnlockScript](unlockScript).left
      .map { error =>
        ApiError.InternalServerError(s"Failed to deserialize unlock script: $error")
      }
      .flatMap {
        case UnlockScript.P2PKH(publickKey) =>
          Right(publickKey)
        case _ =>
          Left(ApiError.BadRequest(s"Invalid unlock script, require P2PKH"))
      }
}
