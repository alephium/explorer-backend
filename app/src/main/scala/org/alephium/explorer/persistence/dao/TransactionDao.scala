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

package org.alephium.explorer.persistence.dao

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.TransactionQueries._
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
      address: ApiAddress
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[Int] =
    run(countAddressTransactions(address)).map(_.headOption.getOrElse(0))

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
