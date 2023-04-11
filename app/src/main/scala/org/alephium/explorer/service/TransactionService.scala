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

package org.alephium.explorer.service

import java.math.BigInteger
import java.time.Instant

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import io.vertx.core.buffer.Buffer
import org.reactivestreams.Publisher
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.TransactionCache
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.dao.{MempoolDao, TransactionDao}
import org.alephium.explorer.persistence.queries.TransactionQueries._
import org.alephium.explorer.util.TimeUtil
import org.alephium.json.Json.write
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{Address, TokenId, TransactionId}
import org.alephium.util.{Duration, TimeStamp, U256}

trait TransactionService {

  def getTransaction(transactionHash: TransactionId)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[TransactionLike]]

  def getTransactionsByAddress(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]]

  def getTransactionsByAddressSQL(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]]

  def getTransactionsByAddressTimeRangedSQL(address: Address,
                                            fromTime: TimeStamp,
                                            toTime: TimeStamp,
                                            pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]]

  def getTransactionsByAddresses(addresses: ArraySeq[Address], pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]]

  def listMempoolTransactionsByAddress(address: Address)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[MempoolTransaction]]

  def getTransactionsNumberByAddress(address: Address)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Int]

  def getBalance(address: Address)(implicit ec: ExecutionContext,
                                   dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)]

  def getTokenBalance(address: Address, token: TokenId)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)]

  def getTotalNumber()(implicit cache: TransactionCache): Int

  def areAddressesActive(addresses: ArraySeq[Address])(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Boolean]]

  def listMempoolTransactions(pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[MempoolTransaction]]

  def listTokens(pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[TokenId]]

  def listTokenTransactions(token: TokenId, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]]

  def listTokenAddresses(token: TokenId, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Address]]

  def listAddressTokens(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[TokenId]]

  def listAddressTokenTransactions(address: Address, token: TokenId, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]]

  def hasAddressMoreTxsThan(address: Address, from: TimeStamp, to: TimeStamp, threshold: Int)(
      implicit ec: ExecutionContext,
      ac: ActorSystem,
      dc: DatabaseConfig[PostgresProfile]): Future[Boolean]

  def exportTransactionsByAddress(address: Address,
                                  from: TimeStamp,
                                  to: TimeStamp,
                                  exportType: ExportType,
                                  batchSize: Int)(
      implicit ec: ExecutionContext,
      ac: ActorSystem,
      dc: DatabaseConfig[PostgresProfile]): Publisher[Buffer]

  def getAmountHistory(address: Address,
                       from: TimeStamp,
                       to: TimeStamp,
                       intervalType: IntervalType)(
      implicit ec: ExecutionContext,
      ac: ActorSystem,
      dc: DatabaseConfig[PostgresProfile]): Publisher[Buffer]
}

object TransactionService extends TransactionService {

  def getTransaction(transactionHash: TransactionId)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[TransactionLike]] =
    TransactionDao.get(transactionHash).flatMap {
      case None     => MempoolDao.get(transactionHash).map(_.map(PendingTransaction.from))
      case Some(tx) => Future.successful(Some(AcceptedTransaction.from(tx)))
    }

  def getTransactionsByAddress(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] =
    TransactionDao.getByAddress(address, pagination)

  def getTransactionsByAddressSQL(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] =
    TransactionDao.getByAddressSQL(address, pagination)

  def getTransactionsByAddressTimeRangedSQL(address: Address,
                                            fromTime: TimeStamp,
                                            toTime: TimeStamp,
                                            pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] =
    TransactionDao.getByAddressTimeRangedSQL(address, fromTime, toTime, pagination)

  def getTransactionsByAddresses(addresses: ArraySeq[Address], pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] =
    TransactionDao.getByAddresses(addresses, pagination)

  def listMempoolTransactionsByAddress(address: Address)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[MempoolTransaction]] = {
    MempoolDao.listByAddress(address)
  }

  def getTransactionsNumberByAddress(address: Address)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Int] =
    TransactionDao.getNumberByAddressSQLNoJoin(address)

  def getBalance(address: Address)(implicit ec: ExecutionContext,
                                   dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
    TransactionDao.getBalance(address)

  def getTokenBalance(address: Address, token: TokenId)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
    TransactionDao.getTokenBalance(address, token)

  def listMempoolTransactions(pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[MempoolTransaction]] = {
    MempoolDao.list(pagination)
  }

  def listTokens(pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[TokenId]] =
    TransactionDao.listTokens(pagination)

  def listTokenTransactions(token: TokenId, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] =
    TransactionDao.listTokenTransactions(token, pagination)

  def listAddressTokens(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[TokenId]] =
    TransactionDao.listAddressTokens(address, pagination)

  def listTokenAddresses(token: TokenId, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Address]] =
    TransactionDao.listTokenAddresses(token, pagination)

  def listAddressTokenTransactions(address: Address, token: TokenId, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] =
    TransactionDao.listAddressTokenTransactions(address, token, pagination)

  def areAddressesActive(addresses: ArraySeq[Address])(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Boolean]] =
    TransactionDao.areAddressesActive(addresses)

  def getTotalNumber()(implicit cache: TransactionCache): Int =
    cache.getMainChainTxnCount()

  def hasAddressMoreTxsThan(address: Address, from: TimeStamp, to: TimeStamp, threshold: Int)(
      implicit ec: ExecutionContext,
      ac: ActorSystem,
      dc: DatabaseConfig[PostgresProfile]): Future[Boolean] = {
    run(hasAddressMoreTxsThanQuery(address, from, to, threshold))
  }
  def exportTransactionsByAddress(address: Address,
                                  fromTime: TimeStamp,
                                  toTime: TimeStamp,
                                  exportType: ExportType,
                                  batchSize: Int)(
      implicit ec: ExecutionContext,
      ac: ActorSystem,
      dc: DatabaseConfig[PostgresProfile]): Publisher[Buffer] = {

    transactionsPublisher(
      address,
      exportType,
      transactionSource(address, fromTime, toTime, batchSize)
    )

  }

  def getAmountHistory(address: Address,
                       from: TimeStamp,
                       to: TimeStamp,
                       intervalType: IntervalType)(
      implicit ec: ExecutionContext,
      ac: ActorSystem,
      dc: DatabaseConfig[PostgresProfile]): Publisher[Buffer] = {

    val timeranges = amountHistoryTimeRanges(from, to, intervalType)

    Source(timeranges)
      .mapAsync(1) {
        case (from, to) =>
          run(sumAddressOutputs(address, from, to)).map(res => (res, from, to))
      }
      .mapAsync(1) {
        case (outs, from, to) =>
          run(sumAddressInputs(address, from, to)).map(res => (outs, res, to))
      }
      .collect {
        case (outs, ins, to) if outs != U256.Zero || ins != U256.Zero =>
          (outs, ins, to)
      }
      .scan((BigInteger.ZERO, TimeStamp.zero)) {
        case ((sum, _), (outs, ins, to)) =>
          val diff   = outs.v.subtract(ins.v)
          val newSum = sum.add(diff)
          (newSum, to)
      }
      .map {
        case (diff, to) =>
          if (to == TimeStamp.zero) {
            Buffer.buffer("to,diff\n")
          } else {
            val dec =
              new java.math.BigDecimal(diff).divide(new java.math.BigDecimal(ALPH.oneAlph.v))
            val toI         = to.millis
            val str: String = s"$toI,$dec,$diff\n"
            Buffer.buffer(str)
          }
      }
      .toMat(Sink.asPublisher[Buffer](false))(Keep.right)
      .run()
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private def amountHistoryTimeRanges(
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType): ArraySeq[(TimeStamp, TimeStamp)] = {
    val fromTruncated =
      TimeStamp.unsafe(
        Instant
          .ofEpochMilli(
            if (from.isBefore(ALPH.LaunchTimestamp)) ALPH.LaunchTimestamp.millis else from.millis
          )
          .truncatedTo(intervalType.chronoUnit)
          .toEpochMilli
      )

    val first = (ALPH.GenesisTimestamp, fromTruncated.minusUnsafe(Duration.ofMillisUnsafe(1)))
    first +: TimeUtil.buildTimestampRange(fromTruncated,
                                          to,
                                          (intervalType.duration - Duration.ofMillisUnsafe(1)).get)
  }

  private def transactionSource(address: Address, from: TimeStamp, to: TimeStamp, batchSize: Int)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Source[ArraySeq[Transaction], NotUsed] = {
    Source
      .fromPublisher(stream(streamTxByAddressQR(address, from, to)))
      .grouped(batchSize)
      .mapAsync(1) { hashes =>
        run(getTransactionsNoJoin(ArraySeq.from(hashes)))
      }
  }

  private def bufferPublisher(source: Source[String, NotUsed])(
      implicit ac: ActorSystem): Publisher[Buffer] = {
    source
      .map(Buffer.buffer)
      .toMat(Sink.asPublisher[Buffer](false))(Keep.right)
      .run()
  }

  def transactionsPublisher(address: Address,
                            exportType: ExportType,
                            source: Source[ArraySeq[Transaction], NotUsed])(
      implicit actorSystem: ActorSystem
  ): Publisher[Buffer] = {
    bufferPublisher {
      exportType match {
        case ExportType.CSV =>
          val headerSource = Source(ArraySeq(Transaction.csvHeader))
          val csvSource    = source.map(_.map(_.toCsv(address)).mkString)
          headerSource ++ csvSource
        case ExportType.JSON =>
          source
            .map(write(_))
            .intersperse("[", ",\n", "]")
      }
    }
  }
}
