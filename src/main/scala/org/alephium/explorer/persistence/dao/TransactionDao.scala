package org.alephium.explorer.persistence.dao

import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.explorer.api.model.{Address, Transaction}
import org.alephium.explorer.persistence.DBRunner
import org.alephium.explorer.persistence.queries.TransactionQueries

trait TransactionDao {
  def get(hash: Transaction.Hash): Future[Option[Transaction]]
  def getByAddress(address: Address): Future[Seq[Transaction]]
  def getBalance(address: Address): Future[Long]
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

    def getByAddress(address: Address): Future[Seq[Transaction]] =
      run(getTransactionsByAddress(address))

    def getBalance(address: Address): Future[Long] =
      run(getBalanceAction(address))
  }
}
