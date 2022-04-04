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

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.dao.{TransactionDao, UnconfirmedTxDao}
import org.alephium.util.U256

trait TransactionService {
  def getTransaction(transactionHash: Transaction.Hash): Future[Option[TransactionLike]]
  def getTransactionsByAddress(address: Address, pagination: Pagination): Future[Seq[Transaction]]
  def getTransactionsByAddressSQL(address: Address,
                                  pagination: Pagination): Future[Seq[Transaction]]
  def getTransactionsNumberByAddress(address: Address): Future[Int]
  def getBalance(address: Address): Future[(U256, U256)]
  def getTotalNumber(): Future[Int]
}

object TransactionService {
  def apply(transactionDao: TransactionDao, utransactionDao: UnconfirmedTxDao)(
      implicit executionContext: ExecutionContext): TransactionService =
    new Impl(transactionDao, utransactionDao)

  private class Impl(transactionDao: TransactionDao, utransactionDao: UnconfirmedTxDao)(
      implicit executionContext: ExecutionContext)
      extends TransactionService {
    def getTransaction(transactionHash: Transaction.Hash): Future[Option[TransactionLike]] =
      transactionDao.get(transactionHash).flatMap {
        case None     => utransactionDao.get(transactionHash)
        case Some(tx) => Future.successful(Some(ConfirmedTransaction.from(tx)))
      }

    def getTransactionsByAddress(address: Address,
                                 pagination: Pagination): Future[Seq[Transaction]] =
      transactionDao.getByAddress(address, pagination)

    def getTransactionsByAddressSQL(address: Address,
                                    pagination: Pagination): Future[Seq[Transaction]] =
      transactionDao.getByAddressSQL(address, pagination)

    def getTransactionsNumberByAddress(address: Address): Future[Int] =
      transactionDao.getNumberByAddressSQLNoJoin(address)

    def getBalance(address: Address): Future[(U256, U256)] =
      transactionDao.getBalanceSQL(address)

    def getTotalNumber(): Future[Int] =
      transactionDao.getTotalNumber()
  }
}
