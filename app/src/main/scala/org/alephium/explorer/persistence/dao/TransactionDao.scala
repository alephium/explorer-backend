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
import slick.jdbc.JdbcProfile

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DBRunner
import org.alephium.explorer.persistence.queries.TransactionQueries
import org.alephium.util.U256

trait TransactionDao {
  def get(hash: Transaction.Hash): Future[Option[Transaction]]
  def getByAddress(address: Address, pagination: Pagination): Future[Seq[Transaction]]
  def getByAddressSQL(address: Address, pagination: Pagination): Future[Seq[Transaction]]
  def getNumberByAddress(address: Address): Future[Int]
  def getNumberByAddressSQL(address: Address): Future[Int]
  def getBalance(address: Address): Future[(U256, U256)]
  def getBalanceSQL(address: Address): Future[(U256, U256)]
  def getBalanceSQLNoJoin(address: Address): Future[(U256, U256)]
}

object TransactionDao {
  def apply(config: DatabaseConfig[JdbcProfile])(
      implicit executionContext: ExecutionContext): TransactionDao =
    new Impl(config)

  private class Impl(val config: DatabaseConfig[JdbcProfile])(
      implicit val executionContext: ExecutionContext)
      extends TransactionDao
      with TransactionQueries
      with DBRunner {

    def get(hash: Transaction.Hash): Future[Option[Transaction]] =
      run(getTransactionAction(hash))

    def getByAddress(address: Address, pagination: Pagination): Future[Seq[Transaction]] =
      run(getTransactionsByAddress(address, pagination))

    def getByAddressSQL(address: Address, pagination: Pagination): Future[Seq[Transaction]] = {
      val offset = pagination.offset
      val limit  = pagination.limit
      val toDrop = offset * limit
      for {
        txHashesTs <- run(getTxHashesByAddressQuerySQLNoJoin(address, toDrop, limit))
        txHashes = txHashesTs.map(_._1)
        insVec <- run(inputsFromTxsSQL(txHashes))
        ousVec <- run(outputsFromTxsSQL(txHashes))
        gasVec <- run(gasFromTxsSQL(txHashes))
      } yield {
        txHashesTs.map {
          case (tx, bh, ts) =>
            val ins =
              insVec.filter(_._1 == tx).sortBy(_._2).map {
                case (_, _, hint, key, unlockScript, txHash, address, amount) =>
                  Input(Output.Ref(hint, key), unlockScript, txHash, address, amount)
              }
            val ous = ousVec.filter(_._1 == tx).sortBy(_._2).map {
              case (_, _, hint, key, amount, address, lockTime, spent) =>
                Output(hint, key, amount, address, lockTime, spent)
            }
            val (gasAmount, gasPrice) = gasVec
              .filter(_._1 == tx)
              .map { case (_, s, g) => (s, g) }
              .headOption
              .getOrElse((0, U256.Zero))
            Transaction(tx, bh, ts, ins, ous, gasAmount, gasPrice)
        }
      }
    }

    def getNumberByAddress(address: Address): Future[Int] =
      run(countAddressTransactions(address))

    def getNumberByAddressSQL(address: Address): Future[Int] =
      run(countAddressTransactionsSQL(address)).map(_.headOption.getOrElse(0))

    def getBalance(address: Address): Future[(U256, U256)] =
      run(getBalanceAction(address))

    def getBalanceSQL(address: Address): Future[(U256, U256)] =
      run(getBalanceActionSQL(address))

    def getBalanceSQLNoJoin(address: Address): Future[(U256, U256)] =
      run(getBalanceActionSQLNoJoin(address))
  }
}
