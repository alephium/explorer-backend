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

import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.TransactionQueries._
import org.alephium.util.U256

object TransactionDao {

  def get(hash: Transaction.Hash)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[Transaction]] =
    run(getTransactionAction(hash))

  def getByAddress(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
    run(getTransactionsByAddress(address, pagination))

  def getByAddressSQL(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
    run(getTransactionsByAddressSQL(address, pagination))

  def getNumberByAddress(address: Address)(implicit ec: ExecutionContext,
                                           dc: DatabaseConfig[PostgresProfile]): Future[Int] =
    run(countAddressTransactions(address))

  def getNumberByAddressSQL(address: Address)(implicit ec: ExecutionContext,
                                              dc: DatabaseConfig[PostgresProfile]): Future[Int] =
    run(countAddressTransactionsSQL(address)).map(_.headOption.getOrElse(0))

  def getNumberByAddressSQLNoJoin(address: Address)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Int] =
    run(countAddressTransactionsSQLNoJoin(address)).map(_.headOption.getOrElse(0))

  def getBalance(address: Address)(implicit ec: ExecutionContext,
                                   dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
    run(getBalanceAction(address))

}
