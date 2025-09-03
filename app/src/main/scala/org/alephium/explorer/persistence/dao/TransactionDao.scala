// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.dao

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.AddressTxCountCache
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model.{AddressTotalTransactionsEntity, AppState}
import org.alephium.explorer.persistence.queries.AppStateQueries
import org.alephium.explorer.persistence.queries.TransactionQueries._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.protocol.ALPH
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.TransactionId
import org.alephium.util.{TimeStamp, U256}

object TransactionDao {

  def get(hash: TransactionId)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[Transaction]] =
    run(getTransactionAction(hash))

  def getByAddress(address: ApiAddress, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    run(getTransactionsByAddress(address, pagination))

  def getByAddresses(
      addresses: ArraySeq[ApiAddress],
      fromTime: Option[TimeStamp],
      toTime: Option[TimeStamp],
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    run(getTransactionsByAddresses(addresses, fromTime, toTime, pagination))

  def getByAddressTimeRanged(
      address: ApiAddress,
      fromTime: TimeStamp,
      toTime: TimeStamp,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    run(getTransactionsByAddressTimeRanged(address, fromTime, toTime, pagination))

  def getLatestTransactionInfoByAddress(address: ApiAddress)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TransactionInfo]] =
    run(getLatestTransactionInfoByAddressAction(address).map(_.map { tx =>
      TransactionInfo(tx.txHash, tx.blockHash, tx.blockTimestamp, tx.coinbase)
    }))

  def getNumberByAddress(
      address: ApiAddress,
      cache: AddressTxCountCache
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Int] = {
    for {
      lastFinalizedTime <- run(
        AppStateQueries
          .get(AppState.LastFinalizedInputTime)
          .map(_.map(_.time).getOrElse(TimeStamp.zero))
      )
      cacheValue <- cache.getAddressTotalTransaction(address)
      lastCacheUpdate = cacheValue.map(_.lastUpdate).getOrElse(ALPH.GenesisTimestamp)
      newFinalizedCount <- run(
        countAddressTransactionsTimeRanged(
          address,
          lastCacheUpdate,
          Some(lastFinalizedTime)
        )
      )
      _ <- updateAddressTotalTransaction(
        address,
        cacheValue,
        newFinalizedCount,
        lastFinalizedTime,
        cache
      )
      nonFinalizedCount <- run(
        countAddressTransactionsTimeRanged(
          address,
          lastFinalizedTime,
          None
        )
      )
    } yield {
      val cacheCount = cacheValue.map(_.total).getOrElse(0)
      cacheCount + newFinalizedCount + nonFinalizedCount
    }
  }

  /*
   * Updates the address total transaction count in the cache.
   * If the address is not in the cache, it will create a new entry.
   * If the address is in the cache, it will update the total count if the new finalize count is greater than 0
   * or if the last update in the cache is older than the last finalized time.
   * This prevents unnecessary updates to the cache.
   */
  private def updateAddressTotalTransaction(
      address: ApiAddress,
      cacheValue: Option[AddressTotalTransactionsEntity],
      newFinalizedCount: Int,
      lastFinalizedTime: TimeStamp,
      cache: AddressTxCountCache
  ): Future[Unit] = {
    cacheValue match {
      case Some(value) =>
        if (newFinalizedCount > 0 || value.lastUpdate < lastFinalizedTime) {
          // We only update the cache if the new finalize count is greater than 0 or
          // If the last update in the cache is older than the last finalized time
          // This prevents unnecessary updates
          val total = value.total + newFinalizedCount
          val addressTotal = AddressTotalTransactionsEntity(
            address,
            total,
            lastFinalizedTime
          )
          cache.updateAddressTotalTransaction(addressTotal)
        } else {
          // No need to update, the cache is already up to date
          Future.successful(())
        }
      case None =>
        // No cache value, first time we call that address, we need to create a new entry
        // unless total is 0, then we don't want to create an entry
        // so avoid flooding the cache with unexisting addresses
        if (newFinalizedCount != 0) {
          val addressTotal = AddressTotalTransactionsEntity(
            address,
            newFinalizedCount,
            lastFinalizedTime
          )
          cache.updateAddressTotalTransaction(addressTotal)
        } else {
          Future.successful(())
        }
    }
  }

  def getBalance(
      address: ApiAddress,
      latestFinalizedTimestamp: TimeStamp
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
    run(getBalanceAction(address, latestFinalizedTimestamp))

  def areAddressesActive(
      addresses: ArraySeq[ApiAddress]
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      groupConfig: GroupConfig
  ): Future[ArraySeq[Boolean]] =
    run(areAddressesActiveAction(addresses))
}
