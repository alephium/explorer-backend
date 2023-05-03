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
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import io.reactivex.rxjava3.core.Flowable
import io.vertx.core.buffer.Buffer
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.TransactionCache
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.dao.{MempoolDao, TransactionDao}
import org.alephium.explorer.persistence.queries.TransactionQueries._
import org.alephium.protocol.model.{Address, TokenId, TransactionId}
import org.alephium.util.{TimeStamp, U256}

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
      dc: DatabaseConfig[PostgresProfile]): Future[Boolean]

  def exportTransactionsByAddress(address: Address,
                                  fromTime: TimeStamp,
                                  toTime: TimeStamp,
                                  batchSize: Int,
                                  paralellism: Int)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Flowable[Buffer]
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
      dc: DatabaseConfig[PostgresProfile]): Future[Boolean] = {
    run(hasAddressMoreTxsThanQuery(address, from, to, threshold))
  }
  def exportTransactionsByAddress(address: Address,
                                  fromTime: TimeStamp,
                                  toTime: TimeStamp,
                                  batchSize: Int,
                                  paralellism: Int)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Flowable[Buffer] = {

    transactionsFlowable(
      address,
      transactionSource(address, fromTime, toTime, batchSize, paralellism)
    )

  }

  private def transactionSource(address: Address,
                                from: TimeStamp,
                                to: TimeStamp,
                                batchSize: Int,
                                paralellism: Int)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Flowable[ArraySeq[Transaction]] = {
    Flowable
      .fromPublisher(stream(streamTxByAddressQR(address, from, to)))
      .buffer(batchSize)
      .concatMapEager(({ hashes =>
        Flowable.fromCompletionStage(
          run(getTransactionsNoJoin(ArraySeq.from(hashes.asScala))).asJava
        )
      }), paralellism, 1)
  }

  private def bufferFlowable(source: Flowable[String]): Flowable[Buffer] = {
    source
      .map(Buffer.buffer)
  }

  def transactionsFlowable(address: Address,
                           source: Flowable[ArraySeq[Transaction]]): Flowable[Buffer] = {
    bufferFlowable {
      val headerSource = Flowable.just(Transaction.csvHeader)
      val csvSource    = source.map(_.map(_.toCsv(address)).mkString)
      headerSource.mergeWith(csvSource)
    }
  }

  def outputsFlowable(source: Flowable[ArraySeq[Output]]): Flowable[Buffer] = {
    bufferFlowable {
      source.map(_.map(_.toString()).mkString)
    }
  }
}
