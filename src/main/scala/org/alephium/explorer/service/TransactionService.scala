package org.alephium.explorer.service

import scala.concurrent.Future

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.Transaction
import org.alephium.explorer.persistence.dao.TransactionDao

trait TransactionService {
  def getTransaction(transactionId: Hash): Future[Option[Transaction]]
}

object TransactionService {
  def apply(transactionDao: TransactionDao): TransactionService =
    new Impl(transactionDao)

  private class Impl(transactionDao: TransactionDao) extends TransactionService {
    def getTransaction(transactionId: Hash): Future[Option[Transaction]] =
      transactionDao.get(transactionId)
  }
}
