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
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import akka.util.ByteString
import io.reactivex.rxjava3.core.Flowable
import io.vertx.core.buffer.Buffer
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.TransactionCache
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.dao.{MempoolDao, TransactionDao}
import org.alephium.explorer.persistence.queries.InputQueries
import org.alephium.explorer.persistence.queries.TransactionQueries._
import org.alephium.explorer.util.TimeUtil
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{Address, GroupIndex, TransactionId}
import org.alephium.util.{Duration, TimeStamp, U256}

trait TransactionService {

  def getTransaction(transactionHash: TransactionId)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TransactionLike]]

  def getTransactionsByAddress(
      address: Address,
      groupIndex: Option[GroupIndex],
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]]

  def getTransactionsByAddressTimeRanged(
      address: Address,
      fromTime: TimeStamp,
      toTime: TimeStamp,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]]

  def getLatestTransactionInfoByAddress(address: Address)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TransactionInfo]]

  def getTransactionsByAddresses(
      addresses: ArraySeq[Address],
      fromTime: Option[TimeStamp],
      toTime: Option[TimeStamp],
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]]

  def listMempoolTransactionsByAddress(address: Address)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]]

  def getTransactionsNumberByAddress(
      address: Address,
      groupIndex: Option[GroupIndex]
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[Int]

  def getBalance(
      address: Address,
      groupIndex: Option[GroupIndex],
      latestFinalizedBlock: TimeStamp
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)]

  def getTotalNumber()(implicit cache: TransactionCache): Int

  def areAddressesActive(
      addresses: ArraySeq[Address]
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Boolean]]

  def listMempoolTransactions(pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]]

  def hasAddressMoreTxsThan(address: Address, from: TimeStamp, to: TimeStamp, threshold: Int)(
      implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Boolean]

  def getUnlockScript(
      address: Address
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[Option[ByteString]]

  def exportTransactionsByAddress(
      address: Address,
      fromTime: TimeStamp,
      toTime: TimeStamp,
      batchSize: Int,
      paralellism: Int
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Flowable[Buffer]

  def getAmountHistoryDEPRECATED(
      address: Address,
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType,
      paralellism: Int
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Flowable[Buffer]

  def getAmountHistory(
      address: Address,
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
      address: Address,
      groupIndex: Option[GroupIndex],
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    TransactionDao.getByAddress(address, groupIndex, pagination)

  def getTransactionsByAddressTimeRanged(
      address: Address,
      fromTime: TimeStamp,
      toTime: TimeStamp,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    TransactionDao.getByAddressTimeRanged(address, fromTime, toTime, pagination)

  def getLatestTransactionInfoByAddress(address: Address)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TransactionInfo]] =
    TransactionDao.getLatestTransactionInfoByAddress(address)

  def getTransactionsByAddresses(
      addresses: ArraySeq[Address],
      fromTime: Option[TimeStamp],
      toTime: Option[TimeStamp],
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    TransactionDao.getByAddresses(addresses, fromTime, toTime, pagination)

  def listMempoolTransactionsByAddress(address: Address)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]] = {
    MempoolDao.listByAddress(address)
  }

  def getTransactionsNumberByAddress(
      address: Address,
      groupIndex: Option[GroupIndex]
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[Int] =
    TransactionDao.getNumberByAddress(address, groupIndex)

  def getBalance(
      address: Address,
      groupIndex: Option[GroupIndex],
      latestFinalizedBlock: TimeStamp
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
    TransactionDao.getBalance(address, groupIndex, latestFinalizedBlock)

  def listMempoolTransactions(pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]] = {
    MempoolDao.list(pagination)
  }

  def areAddressesActive(
      addresses: ArraySeq[Address]
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Boolean]] =
    TransactionDao.areAddressesActive(addresses)

  def getTotalNumber()(implicit cache: TransactionCache): Int =
    cache.getMainChainTxnCount()

  def hasAddressMoreTxsThan(address: Address, from: TimeStamp, to: TimeStamp, threshold: Int)(
      implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Boolean] = {
    run(hasAddressMoreTxsThanQuery(address, from, to, threshold))
  }
  def exportTransactionsByAddress(
      address: Address,
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

  private def getInOutAmountDEPRECATED(address: Address, from: TimeStamp, to: TimeStamp)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[(U256, U256, TimeStamp)] = {
    run(
      for {
        in  <- sumAddressInputsDEPRECATED(address, from, to)
        out <- sumAddressOutputsDEPRECATED(address, from, to)
      } yield {
        (in, out, to)
      }
    )
  }

  def getUnlockScript(address: Address)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[ByteString]] = {
    run(InputQueries.getUnlockScript(address))
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def getAmountHistoryDEPRECATED(
      address: Address,
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType,
      paralellism: Int
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Flowable[Buffer] = {
    val timeranges = amountHistoryTimeRanges(from, to, intervalType)
    val amountHistory = Flowable
      .fromIterable(timeranges.asJava)
      .concatMapEager(
        { case (from: TimeStamp, to: TimeStamp) =>
          Flowable.fromCompletionStage(getInOutAmountDEPRECATED(address, from, to).asJava)
        },
        paralellism,
        1
      )
      .filter {
        // Ignore time ranges without data
        case (in, out, _) => in != U256.Zero || out != U256.Zero
      }
      .scan(
        (BigInteger.ZERO, TimeStamp.zero),
        { (acc: (BigInteger, TimeStamp), next) =>
          val (sum, _)      = acc
          val (in, out, to) = next
          val diff          = out.v.subtract(in.v)
          val newSum        = sum.add(diff)
          (newSum, to)
        }
      )
      .skip(1) // Drop first elem which is the seed of the scan (0,0)

    amountHistoryToJsonFlowable(
      amountHistory
    )
  }

  def getAmountHistory(
      address: Address,
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

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private def amountHistoryTimeRanges(
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType
  ): ArraySeq[(TimeStamp, TimeStamp)] = {
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
    first +: TimeUtil.buildTimestampRange(
      fromTruncated,
      to,
      (intervalType.duration - Duration.ofMillisUnsafe(1)).get
    )
  }

  private def transactionSource(
      address: Address,
      from: TimeStamp,
      to: TimeStamp,
      batchSize: Int,
      paralellism: Int
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Flowable[ArraySeq[Transaction]] = {
    Flowable
      .fromPublisher(stream(streamTxByAddressQR(address, from, to)))
      .buffer(batchSize)
      .concatMapEager(
        { hashes =>
          Flowable.fromCompletionStage(
            run(getTransactions(ArraySeq.from(hashes.asScala))).asJava
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

  def transactionsFlowable(
      address: Address,
      source: Flowable[ArraySeq[Transaction]]
  ): Flowable[Buffer] = {
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
