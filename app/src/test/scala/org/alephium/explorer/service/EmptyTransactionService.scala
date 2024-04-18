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

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import io.reactivex.rxjava3.core.Flowable
import io.vertx.core.buffer.Buffer
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.TransactionCache
import org.alephium.explorer.service.TransactionService
import org.alephium.protocol.model.{Address, TransactionId}
import org.alephium.util.{TimeStamp, U256}

trait EmptyTransactionService extends TransactionService {
  override def getTransaction(transactionHash: TransactionId)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TransactionLike]] =
    Future.successful(None)

  override def getTransactionsNumberByAddress(
      address: Address
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[Int] =
    Future.successful(0)

  override def getTransactionsByAddress(address: Address, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    Future.successful(ArraySeq.empty)

  override def getTransactionsByAddresses(addresses: ArraySeq[Address], pagination: Pagination)(
      implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    Future.successful(ArraySeq.empty)

  override def getTransactionsByAddressTimeRanged(
      address: Address,
      fromTime: TimeStamp,
      toTime: TimeStamp,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    Future.successful(ArraySeq.empty)

  override def listMempoolTransactionsByAddress(address: Address)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]] = {
    Future.successful(ArraySeq.empty)
  }

  override def getBalance(
      address: Address
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
    Future.successful((U256.Zero, U256.Zero))

  def getTotalNumber()(implicit cache: TransactionCache): Int =
    0

  def listMempoolTransactions(pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]] = ???
  def areAddressesActive(addresses: ArraySeq[Address])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Boolean]] = {
    Future.successful(ArraySeq(true))
  }

  def numberOfActiveAddresses()(implicit cache: TransactionCache): Int = ???

  def holderEstimation()(implicit cache: TransactionCache): Int = ???

  def hasAddressMoreTxsThan(address: Address, from: TimeStamp, to: TimeStamp, threshold: Int)(
      implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Boolean] = ???

  def exportTransactionsByAddress(
      address: Address,
      from: TimeStamp,
      to: TimeStamp,
      batchSize: Int,
      paralellism: Int
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Flowable[Buffer] = ???

  def getAmountHistoryDEPRECATED(
      address: Address,
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType,
      paralellism: Int
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Flowable[Buffer] = ???

  def getAmountHistory(
      address: Address,
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[(TimeStamp, BigInteger)]] = ???
}
