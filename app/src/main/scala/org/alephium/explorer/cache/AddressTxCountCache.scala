// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.cache

import java.util.concurrent.TimeUnit

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.persistence.Database
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.TransactionQueries
import org.alephium.explorer.persistence.schema.AddressTotalTransactionSchema
import org.alephium.util.Service

trait AddressTxCountCache extends Service {
  def getAddressTotalTransaction(
      address: ApiAddress
  ): Future[Option[AddressTotalTransactionsEntity]]

  def updateAddressTotalTransaction(
      value: AddressTotalTransactionsEntity
  ): Future[Unit]

  override def startSelfOnce(): Future[Unit] = Future.successful(())

  override def stopSelfOnce(): Future[Unit] = Future.successful(())

}

final case class InMemoryAddressTxCountCache(database: Database)(implicit
    val executionContext: ExecutionContext
) extends AddressTxCountCache {

  // scalasty:off magic.number
  private val invalidationPeriodInDays = 14L // TODO make it configurable
  // scalasty:on magic.number

  val cache: Cache[ApiAddress, AddressTotalTransactionsEntity] =
    Caffeine
      .newBuilder()
      .expireAfterWrite(invalidationPeriodInDays, TimeUnit.DAYS)
      .build[ApiAddress, AddressTotalTransactionsEntity]()

  override def getAddressTotalTransaction(
      address: ApiAddress
  ): Future[Option[AddressTotalTransactionsEntity]] = {
    val valueOrNull = cache.getIfPresent(address)

    if (valueOrNull == null) {
      run(TransactionQueries.getAddressTotalTransaction(address))(database.databaseConfig)
    } else {
      Future.successful(Some(valueOrNull))
    }
  }

  override def updateAddressTotalTransaction(
      value: AddressTotalTransactionsEntity
  ): Future[Unit] = Future.successful {
    cache.put(value.address, value)
  }

  override def subServices: ArraySeq[Service] = ArraySeq(database)
}

final case class DBAddressTxCountCache(database: Database)(implicit
    val executionContext: ExecutionContext
) extends AddressTxCountCache {
  override def getAddressTotalTransaction(
      address: ApiAddress
  ): Future[Option[AddressTotalTransactionsEntity]] =
    run(TransactionQueries.getAddressTotalTransaction(address))(database.databaseConfig)

  override def updateAddressTotalTransaction(
      value: AddressTotalTransactionsEntity
  ): Future[Unit] =
    run(
      AddressTotalTransactionSchema.table
        .insertOrUpdate(value)
    )(database.databaseConfig).map(_ => ())

  override def subServices: ArraySeq[Service] = ArraySeq(database)
}
