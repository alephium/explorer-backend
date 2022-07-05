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

import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.TransactionCache
import org.alephium.explorer.persistence.dao.{TransactionDao, UnconfirmedTxDao}
import org.alephium.util.U256

trait TransactionService {
  def getTransaction(transactionHash: Transaction.Hash)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[TransactionLike]]

  def getOutputRefTransaction(hash: Hash)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[ConfirmedTransaction]]

  def getTransactionsByAddress(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]]

  def getTransactionsByAddressSQL(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]]

  def getTransactionsNumberByAddress(address: Address)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Int]

  def getBalance(address: Address)(implicit ec: ExecutionContext,
                                   dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)]

  def getTotalNumber()(implicit cache: TransactionCache): Int
}

object TransactionService extends TransactionService {

  def getTransaction(transactionHash: Transaction.Hash)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[TransactionLike]] =
    TransactionDao.get(transactionHash).flatMap {
      case None     => UnconfirmedTxDao.get(transactionHash)
      case Some(tx) => Future.successful(Some(ConfirmedTransaction.from(tx)))
    }

  def getOutputRefTransaction(hash: Hash)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[ConfirmedTransaction]] =
    TransactionDao
      .getOutputRefTransaction(hash)
      .map(_.map { tx =>
        ConfirmedTransaction.from(tx)
      })

  def getTransactionsByAddress(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
    TransactionDao.getByAddress(address, pagination)

  def getTransactionsByAddressSQL(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
    TransactionDao.getByAddressSQL(address, pagination)

  def getTransactionsNumberByAddress(address: Address)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Int] =
    TransactionDao.getNumberByAddressSQLNoJoin(address)

  def getBalance(address: Address)(implicit ec: ExecutionContext,
                                   dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
    TransactionDao.getBalance(address)

  def getTotalNumber()(implicit cache: TransactionCache): Int =
    cache.getMainChainTxnCount()
}
