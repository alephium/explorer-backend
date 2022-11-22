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
import org.alephium.explorer.persistence.dao.{TransactionDao, UnconfirmedTxDao}
import org.alephium.explorer.persistence.queries.TransactionQueries._
import org.alephium.json.Json.write
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{TokenId, TransactionId}
import org.alephium.util.{TimeStamp, U256}

trait TransactionService {

  def getTransaction(transactionHash: TransactionId)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[TransactionLike]]

  def getOutputRefTransaction(hash: Hash)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[Transaction]]

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

  def listUnconfirmedTransactionsByAddress(address: Address)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[UnconfirmedTransaction]]

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

  def listUnconfirmedTransactions(pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[UnconfirmedTransaction]]

  def listTokens(pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[TokenId]]

  def listTokenTransactions(token: TokenId, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]]

  def listTokenAddresses(token: TokenId, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Address]]

  def listAddressTokens(address: Address)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[TokenId]]

  def listAddressTokenTransactions(address: Address, token: TokenId, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]]

  def exportTransactionsByAddress(address: Address,
                                  from: TimeStamp,
                                  to: TimeStamp,
                                  exportType: ExportType)(
      implicit ec: ExecutionContext,
      ac: ActorSystem,
      dc: DatabaseConfig[PostgresProfile]): Publisher[Buffer]
}

object TransactionService extends TransactionService {

  def getTransaction(transactionHash: TransactionId)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[TransactionLike]] =
    TransactionDao.get(transactionHash).flatMap {
      case None     => UnconfirmedTxDao.get(transactionHash)
      case Some(tx) => Future.successful(Some(ConfirmedTransaction.from(tx)))
    }

  def getOutputRefTransaction(hash: Hash)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[Transaction]] =
    TransactionDao
      .getOutputRefTransaction(hash)

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

  def listUnconfirmedTransactionsByAddress(address: Address)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[UnconfirmedTransaction]] = {
    UnconfirmedTxDao.listByAddress(address)
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

  def listUnconfirmedTransactions(pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[UnconfirmedTransaction]] = {
    UnconfirmedTxDao.list(pagination)
  }

  def listTokens(pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[TokenId]] =
    TransactionDao.listTokens(pagination)

  def listTokenTransactions(token: TokenId, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] =
    TransactionDao.listTokenTransactions(token, pagination)

  def listAddressTokens(address: Address)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[TokenId]] =
    TransactionDao.listAddressTokens(address)

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

  def exportTransactionsByAddress(address: Address,
                                  fromTime: TimeStamp,
                                  toTime: TimeStamp,
                                  exportType: ExportType)(
      implicit ec: ExecutionContext,
      ac: ActorSystem,
      dc: DatabaseConfig[PostgresProfile]): Publisher[Buffer] = {

    transactionsPublisher(
      address,
      exportType,
      transactionSource(address, fromTime, toTime)
    )

  }

  private def transactionSource(address: Address, from: TimeStamp, to: TimeStamp)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Source[Transaction, NotUsed] =
    Source
      .fromPublisher(stream(streamTxIds(address, from, to)))
      .mapAsync(1)(TransactionDao.get)
      .collect { case Some(tx) => tx }

  private def bufferPublisher(source: Source[String, NotUsed])(
      implicit ac: ActorSystem): Publisher[Buffer] = {
    source
      .map(Buffer.buffer)
      .toMat(Sink.asPublisher[Buffer](false))(Keep.right)
      .run()
  }

  def transactionsPublisher(address: Address,
                            exportType: ExportType,
                            source: Source[Transaction, NotUsed])(
      implicit actorSystem: ActorSystem): Publisher[Buffer] = {
    bufferPublisher {
      exportType match {
        case ExportType.CSV =>
          val headerSource = Source(ArraySeq(Transaction.csvHeader))
          val csvSource    = source.map(_.toCsv(address))
          headerSource ++ csvSource
        case ExportType.JSON =>
          source
            .map(write(_))
            .intersperse("[", ",\n", "]")
      }
    }
  }
}
