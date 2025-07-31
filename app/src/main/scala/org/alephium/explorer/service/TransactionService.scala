// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.math.BigInteger

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import akka.util.ByteString
import io.reactivex.rxjava3.core.Flowable
import io.vertx.core.buffer.Buffer
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.TransactionCache
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.dao.{MempoolDao, TransactionDao}
import org.alephium.explorer.persistence.queries.{InputQueries, TokenQueries}
import org.alephium.explorer.persistence.queries.TransactionQueries._
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.{TokenId, TransactionId}
import org.alephium.util.{TimeStamp, U256}

trait TransactionService {

  def getTransaction(transactionHash: TransactionId)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TransactionLike]]

  def getTransactionsByAddress(
      address: ApiAddress,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]]

  def getTransactionsByAddressTimeRanged(
      address: ApiAddress,
      fromTime: TimeStamp,
      toTime: TimeStamp,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]]

  def getLatestTransactionInfoByAddress(address: ApiAddress)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TransactionInfo]]

  def getTransactionsByAddresses(
      addresses: ArraySeq[ApiAddress],
      fromTime: Option[TimeStamp],
      toTime: Option[TimeStamp],
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]]

  def listMempoolTransactionsByAddress(address: ApiAddress)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]]

  def getTransactionsNumberByAddress(
      address: ApiAddress
  )(implicit
      groupConfig: GroupConfig,
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Int]

  def getBalance(
      address: ApiAddress,
      latestFinalizedBlock: TimeStamp
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)]

  def getTotalNumber()(implicit cache: TransactionCache): Int

  def areAddressesActive(
      addresses: ArraySeq[ApiAddress]
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      groupConfig: GroupConfig
  ): Future[ArraySeq[Boolean]]

  def listMempoolTransactions(pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]]

  def hasAddressMoreTxsThan(
      address: ApiAddress,
      from: TimeStamp,
      to: TimeStamp,
      threshold: Int
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Boolean]

  def getUnlockScript(
      address: ApiAddress
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[Option[ByteString]]

  def exportTransactionsByAddress(
      address: ApiAddress,
      fromTime: TimeStamp,
      toTime: TimeStamp,
      batchSize: Int,
      paralellism: Int
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Flowable[Buffer]

  def getAmountHistory(
      address: ApiAddress,
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[(TimeStamp, BigInteger)]]
}

object TransactionService extends TransactionService {

  def getTransaction(transactionHash: TransactionId)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TransactionLike]] =
    TransactionDao.get(transactionHash).flatMap {
      case None     => MempoolDao.get(transactionHash).map(_.map(PendingTransaction.from))
      case Some(tx) => Future.successful(Some(AcceptedTransaction.from(tx)))
    }

  def getTransactionsByAddress(
      address: ApiAddress,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    TransactionDao.getByAddress(address, pagination)

  def getTransactionsByAddressTimeRanged(
      address: ApiAddress,
      fromTime: TimeStamp,
      toTime: TimeStamp,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    TransactionDao.getByAddressTimeRanged(address, fromTime, toTime, pagination)

  def getLatestTransactionInfoByAddress(address: ApiAddress)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TransactionInfo]] =
    TransactionDao.getLatestTransactionInfoByAddress(address)

  def getTransactionsByAddresses(
      addresses: ArraySeq[ApiAddress],
      fromTime: Option[TimeStamp],
      toTime: Option[TimeStamp],
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    TransactionDao.getByAddresses(addresses, fromTime, toTime, pagination)

  def listMempoolTransactionsByAddress(address: ApiAddress)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]] = {
    MempoolDao.listByAddress(address)
  }

  def getTransactionsNumberByAddress(
      address: ApiAddress
  )(implicit
      groupConfig: GroupConfig,
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Int] =
    TransactionDao.getNumberByAddress(address)

  def getBalance(
      address: ApiAddress,
      latestFinalizedBlock: TimeStamp
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
    TransactionDao.getBalance(address, latestFinalizedBlock)

  def listMempoolTransactions(pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]] = {
    MempoolDao.list(pagination)
  }

  def areAddressesActive(
      addresses: ArraySeq[ApiAddress]
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      groupConfig: GroupConfig
  ): Future[ArraySeq[Boolean]] =
    TransactionDao.areAddressesActive(addresses)

  def getTotalNumber()(implicit cache: TransactionCache): Int =
    cache.getMainChainTxnCount()

  def hasAddressMoreTxsThan(
      address: ApiAddress,
      from: TimeStamp,
      to: TimeStamp,
      threshold: Int
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Boolean] = {
    run(hasAddressMoreTxsThanQuery(address, from, to, threshold))
  }
  def exportTransactionsByAddress(
      address: ApiAddress,
      fromTime: TimeStamp,
      toTime: TimeStamp,
      batchSize: Int,
      paralellism: Int
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Flowable[Buffer] = {

    transactionsFlowable(
      address,
      transactionSource(address, fromTime, toTime, batchSize, paralellism)
    )

  }

  def getUnlockScript(address: ApiAddress)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[ByteString]] = {
    run(InputQueries.getUnlockScript(address))
  }

  def getAmountHistory(
      address: ApiAddress,
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[(TimeStamp, BigInteger)]] = {
    run(
      for {
        inputs  <- sumAddressInputs(address, from, to, intervalType)
        outputs <- sumAddressOutputs(address, from, to, intervalType)
      } yield {
        val ins  = inputs.collect { case (ts, Some(u256)) => (ts, u256) }.toMap
        val outs = outputs.collect { case (ts, Some(u256)) => (ts, u256) }.toMap

        val timestamps = scala.collection.SortedSet.from(ins.keys ++ outs.keys)

        val (_, result) = timestamps
          .foldLeft((BigInteger.ZERO, ArraySeq.empty[(TimeStamp, BigInteger)])) { case (acc, ts) =>
            val (sum, res) = acc

            (ins.get(ts), outs.get(ts)) match {
              case (Some(in), Some(out)) =>
                val diff   = out.v.subtract(in.v)
                val newSum = sum.add(diff)
                (newSum, res :+ (ts, newSum))
              case (Some(in), None) =>
                // No Output, all inputs are spent
                val newSum = sum.subtract(in.v)
                (newSum, res :+ (ts, newSum))
              case (None, Some(out)) =>
                // No Input, all outputs are for the address
                val newSum = sum.add(out.v)
                (newSum, res :+ (ts, newSum))
              case (None, None) =>
                (sum, res)
            }
          }

        result
      }
    )
  }

  def amountHistoryToJsonFlowable(history: Flowable[(BigInteger, TimeStamp)]): Flowable[Buffer] = {
    history
      .concatMap { case (diff, to: TimeStamp) =>
        val str = s"""[${to.millis},"$diff"]"""
        Flowable.just(str, ",")
      }
      .skipLast(1) // Removing latest "," it replace the missing `intersperse`
      .startWith(Flowable.just("""{"amountHistory":["""))
      .concatWith(Flowable.just("]}"))
      .map(Buffer.buffer)
  }

  private def transactionSource(
      address: ApiAddress,
      from: TimeStamp,
      to: TimeStamp,
      batchSize: Int,
      paralellism: Int
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Flowable[(ArraySeq[Transaction], Map[TokenId, FungibleTokenMetadata])] = {
    Flowable
      .fromPublisher(stream(streamTxByAddressQR(address, from, to)))
      .buffer(batchSize)
      .concatMapEager(
        { hashes =>
          Flowable.fromCompletionStage(
            (for {
              txs      <- run(getTransactions(ArraySeq.from(hashes.asScala)))
              metadata <- getTokenMetadata(txs)
            } yield {
              (txs, metadata)
            }).asJava
          )
        },
        paralellism,
        1
      )
  }

  private def bufferFlowable(source: Flowable[String]): Flowable[Buffer] = {
    source
      .map(Buffer.buffer)
  }

  def getTokenMetadata(transactions: ArraySeq[Transaction])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Map[TokenId, FungibleTokenMetadata]] = {
    val tokens: ArraySeq[TokenId] =
      transactions.flatMap(_.outputs).flatMap(_.tokens.map(_.map(_.id))).flatten.distinct

    run(TokenQueries.listFungibleTokenMetadataQuery(tokens)).map(_.map { metadata =>
      metadata.id -> metadata
    }.toMap)
  }

  def transactionsFlowable(
      address: ApiAddress,
      source: Flowable[(ArraySeq[Transaction], Map[TokenId, FungibleTokenMetadata])]
  ): Flowable[Buffer] = {
    bufferFlowable {
      val headerSource = Flowable.just(Transaction.csvHeader)
      val csvSource = source.map { case (txs, metadatas) =>
        txs.map(_.toCsv(address, metadatas)).mkString
      }
      headerSource.mergeWith(csvSource)
    }
  }

  def outputsFlowable(source: Flowable[ArraySeq[Output]]): Flowable[Buffer] = {
    bufferFlowable {
      source.map(_.map(_.toString()).mkString)
    }
  }
}
