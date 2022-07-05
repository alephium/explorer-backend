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

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.TokenQueries._
import org.alephium.explorer.persistence.queries.TransactionQueries._
import org.alephium.protocol.Hash
import org.alephium.util.U256

object TransactionDao {

  def get(hash: Transaction.Hash)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[Transaction]] =
    run(getTransactionAction(hash))

  def getOutputRefTransaction(key: Hash)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[Transaction]] =
    run(getOutputRefTransactionAction(key))

  def getByAddress(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
    run(getTransactionsByAddressNoJoin(address, pagination))

  def getByAddressSQL(address: Address, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
    run(getTransactionsByAddressSQL(address, pagination))

  def getNumberByAddressSQLNoJoin(address: Address)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Int] =
    run(countAddressTransactionsSQLNoJoin(address)).map(_.headOption.getOrElse(0))

  def getBalance(address: Address)(implicit ec: ExecutionContext,
                                   dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
    run(getBalanceAction(address))

  def getTokenBalance(address: Address, token: Hash)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
    run(getTokenBalanceAction(address, token))

  def listTokens(pagination: Pagination)(implicit ec: ExecutionContext,
                                         dc: DatabaseConfig[PostgresProfile]): Future[Seq[Hash]] =
    run(listTokensAction(pagination))

  def listTokenTransactions(token: Hash, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
    run(getTransactionsByToken(token, pagination))

  def listTokenAddresses(token: Hash, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[Address]] =
    run(getAddressesByToken(token, pagination))

  def listAddressTokens(address: Address)(implicit ec: ExecutionContext,
                                          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Hash]] =
    run(listAddressTokensAction(address))

  def listAddressTokenTransactions(address: Address, token: Hash, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
    run(getTokenTransactionsByAddress(address, token, pagination))
}
