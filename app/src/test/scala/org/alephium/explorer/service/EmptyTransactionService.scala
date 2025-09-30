// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.math.BigInteger

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import akka.util.ByteString
import io.reactivex.rxjava3.core.Flowable
import io.vertx.core.buffer.Buffer
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache._
import org.alephium.explorer.service.TransactionService
import org.alephium.protocol
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.TransactionId
import org.alephium.util.{TimeStamp, U256}

trait EmptyTransactionService extends TransactionService {
  override def getTransaction(transactionHash: TransactionId)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TransactionLike]] =
    Future.successful(None)

  override def getTransactionsNumberByAddress(
      address: ApiAddress
  )(implicit
      cache: AddressTxCountCache,
      groupConfig: GroupConfig,
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Int] =
    Future.successful(0)

  override def getTransactionsByAddress(
      address: ApiAddress,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    Future.successful(ArraySeq.empty)

  override def getTransactionsByAddresses(
      addresses: ArraySeq[ApiAddress],
      fromTs: Option[TimeStamp],
      toTs: Option[TimeStamp],
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    Future.successful(ArraySeq.empty)

  override def getTransactionsByAddressTimeRanged(
      address: ApiAddress,
      fromTime: TimeStamp,
      toTime: TimeStamp,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    Future.successful(ArraySeq.empty)

  override def getLatestTransactionInfoByAddress(address: ApiAddress)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TransactionInfo]] =
    Future.successful(None)

  override def listMempoolTransactionsByAddress(address: ApiAddress)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]] = {
    Future.successful(ArraySeq.empty)
  }

  override def getBalance(
      address: ApiAddress,
      from: TimeStamp
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
    Future.successful((U256.Zero, U256.Zero))

  def getTotalNumber()(implicit cache: TransactionCache): Int =
    0

  def listMempoolTransactions(pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]] = ???
  def areAddressesActive(addresses: ArraySeq[ApiAddress])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      groupConfig: GroupConfig
  ): Future[ArraySeq[Boolean]] = {
    Future.successful(ArraySeq(true))
  }
  def hasAddressMoreTxsThan(
      address: ApiAddress,
      from: TimeStamp,
      to: TimeStamp,
      threshold: Int
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Boolean] = ???

  def getUnlockScript(
      address: ApiAddress
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[ByteString]] = Future.successful(None)

  def exportTransactionsByAddress(
      address: ApiAddress,
      from: TimeStamp,
      to: TimeStamp,
      batchSize: Int,
      paralellism: Int
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Flowable[Buffer] = ???

  def getAmountHistory(
      address: ApiAddress,
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[(TimeStamp, BigInteger)]] = ???

  def convertProtocolUnsignedTx(
      unsignedTx: protocol.model.UnsignedTransaction
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[UnsignedTransaction] = ???
}
