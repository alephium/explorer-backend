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

import scala.concurrent.Future

import org.alephium.explorer.api.model.{Address, Transaction}
import org.alephium.explorer.persistence.dao.TransactionDao
import org.alephium.util.U256

trait TransactionService {
  def getTransaction(transactionHash: Transaction.Hash): Future[Option[Transaction]]
  def getTransactionsByAddress(address: Address): Future[Seq[Transaction]]
  def getBalance(address: Address): Future[U256]
}

object TransactionService {
  def apply(transactionDao: TransactionDao): TransactionService =
    new Impl(transactionDao)

  private class Impl(transactionDao: TransactionDao) extends TransactionService {
    def getTransaction(transactionHash: Transaction.Hash): Future[Option[Transaction]] =
      transactionDao.get(transactionHash)

    def getTransactionsByAddress(address: Address): Future[Seq[Transaction]] =
      transactionDao.getByAddress(address)

    def getBalance(address: Address): Future[U256] =
      transactionDao.getBalance(address)
  }
}
