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
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.{DBActionW, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._

trait UTransactionDao {
  def get(hash: Transaction.Hash): Future[Option[UTransaction]]
  def insertMany(utxs: Seq[UTransaction]): Future[Unit]
  def listHashes(): Future[Seq[Transaction.Hash]]
  def removeMany(txs: Seq[Transaction.Hash]): Future[Unit]
  def removeAndInsertMany(toRemove: Seq[Transaction.Hash],
                          toInsert: Seq[UTransaction]): Future[Unit]
}

object UTransactionDao {
  def apply(config: DatabaseConfig[JdbcProfile])(
      implicit executionContext: ExecutionContext): UTransactionDao =
    new Impl(config)

  private class Impl(val config: DatabaseConfig[JdbcProfile])(
      implicit val executionContext: ExecutionContext)
      extends UTransactionDao
      with UTransactionSchema
      with UInputSchema
      with UOutputSchema
      with DBRunner {
    import config.profile.api._

    def get(hash: Transaction.Hash): Future[Option[UTransaction]] = {
      run(for {
        maybeTx <- utransactionsTable.filter(_.hash === hash).result.headOption
        inputs  <- uinputsTable.filter(_.txHash === hash).result
        outputs <- uoutputsTable.filter(_.txHash === hash).result
      } yield {
        maybeTx.map { tx =>
          UTransaction(
            tx.hash,
            tx.chainFrom,
            tx.chainTo,
            inputs.map(_.toApi),
            outputs.map(_.toApi),
            tx.startGas,
            tx.gasPrice
          )
        }
      })
    }

    private def insertManyAction(utxs: Seq[UTransaction]): DBActionW[Unit] = {
      val entities = utxs.map(UTransactionEntity.from)
      val txs      = entities.map { case (tx, _, _) => tx }
      val inputs   = entities.flatMap { case (_, in, _) => in }
      val outputs  = entities.flatMap { case (_, _, out) => out }
      for {
        _ <- DBIOAction.sequence(txs.map(utransactionsTable.insertOrUpdate))
        _ <- DBIOAction.sequence(inputs.map(uinputsTable.insertOrUpdate))
        _ <- DBIOAction.sequence(outputs.map(uoutputsTable.insertOrUpdate))
      } yield ()
    }

    def insertMany(utxs: Seq[UTransaction]): Future[Unit] = {
      run(insertManyAction(utxs))
    }

    def listHashes(): Future[Seq[Transaction.Hash]] = {
      run(utransactionsTable.map(_.hash).result)
    }

    private def removeManyAction(txs: Seq[Transaction.Hash]): DBActionW[Unit] = {
      for {
        _ <- utransactionsTable.filter(_.hash inSet txs).delete
        _ <- uoutputsTable.filter(_.txHash inSet txs).delete
        _ <- uinputsTable.filter(_.txHash inSet txs).delete
      } yield ()
    }

    def removeMany(txs: Seq[Transaction.Hash]): Future[Unit] = {
      run(removeManyAction(txs))
    }

    def removeAndInsertMany(toRemove: Seq[Transaction.Hash],
                            toInsert: Seq[UTransaction]): Future[Unit] = {
      run((for {
        _ <- removeManyAction(toRemove)
        _ <- insertManyAction(toInsert)
      } yield ()).transactionally)
    }
  }
}
