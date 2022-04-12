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
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.{DBActionW, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._

trait UnconfirmedTxDao {
  def get(hash: Transaction.Hash): Future[Option[UnconfirmedTransaction]]
  def insertMany(utxs: Seq[UnconfirmedTransaction]): Future[Unit]
  def listHashes(): Future[Seq[Transaction.Hash]]
  def removeMany(txs: Seq[Transaction.Hash]): Future[Unit]
  def removeAndInsertMany(toRemove: Seq[Transaction.Hash],
                          toInsert: Seq[UnconfirmedTransaction]): Future[Unit]
}

object UnconfirmedTxDao {
  def apply(databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit executionContext: ExecutionContext): UnconfirmedTxDao =
    new Impl(databaseConfig)

  private class Impl(val databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit val executionContext: ExecutionContext)
      extends UnconfirmedTxDao
      with DBRunner {

    def get(hash: Transaction.Hash): Future[Option[UnconfirmedTransaction]] = {
      runAction(for {
        maybeTx <- UnconfirmedTxSchema.table.filter(_.hash === hash).result.headOption
        inputs  <- UInputSchema.table.filter(_.txHash === hash).result
        outputs <- UOutputSchema.table.filter(_.txHash === hash).result
      } yield {
        maybeTx.map { tx =>
          UnconfirmedTransaction(
            tx.hash,
            tx.chainFrom,
            tx.chainTo,
            inputs.map(_.toApi),
            outputs.map(_.toApi),
            tx.gasAmount,
            tx.gasPrice
          )
        }
      })
    }

    private def insertManyAction(utxs: Seq[UnconfirmedTransaction]): DBActionW[Unit] = {
      val entities = utxs.map(UnconfirmedTxEntity.from)
      val txs      = entities.map { case (tx, _, _) => tx }
      val inputs   = entities.flatMap { case (_, in, _) => in }
      val outputs  = entities.flatMap { case (_, _, out) => out }
      for {
        _ <- DBIOAction.sequence(txs.map(UnconfirmedTxSchema.table.insertOrUpdate))
        _ <- DBIOAction.sequence(inputs.map(UInputSchema.table.insertOrUpdate))
        _ <- DBIOAction.sequence(outputs.map(UOutputSchema.table.insertOrUpdate))
      } yield ()
    }

    def insertMany(utxs: Seq[UnconfirmedTransaction]): Future[Unit] = {
      runAction(insertManyAction(utxs))
    }

    def listHashes(): Future[Seq[Transaction.Hash]] = {
      runAction(UnconfirmedTxSchema.table.map(_.hash).result)
    }

    private def removeManyAction(txs: Seq[Transaction.Hash]): DBActionW[Unit] = {
      for {
        _ <- UnconfirmedTxSchema.table.filter(_.hash inSet txs).delete
        _ <- UOutputSchema.table.filter(_.txHash inSet txs).delete
        _ <- UInputSchema.table.filter(_.txHash inSet txs).delete
      } yield ()
    }

    def removeMany(txs: Seq[Transaction.Hash]): Future[Unit] = {
      runAction(removeManyAction(txs))
    }

    def removeAndInsertMany(toRemove: Seq[Transaction.Hash],
                            toInsert: Seq[UnconfirmedTransaction]): Future[Unit] = {
      runAction((for {
        _ <- removeManyAction(toRemove)
        _ <- insertManyAction(toInsert)
      } yield ()).transactionally)
    }
  }
}
