package org.alephium.explorer.service

import scala.concurrent.Future

import org.alephium.explorer.api.model.{Address, Transaction}
import org.alephium.explorer.persistence.dao.TransactionDao

trait TransactionService {
  def getTransaction(transactionHash: Transaction.Hash): Future[Option[Transaction]]
  def getTransactionsByAddress(address: Address): Future[Seq[Transaction]]
}

object TransactionService {
  def apply(transactionDao: TransactionDao): TransactionService =
    new Impl(transactionDao)

  private class Impl(transactionDao: TransactionDao) extends TransactionService {
    def getTransaction(transactionHash: Transaction.Hash): Future[Option[Transaction]] =
      transactionDao.get(transactionHash)

    def getTransactionsByAddress(address: Address): Future[Seq[Transaction]] =
      transactionDao.getByAddress(address)
  }
}
