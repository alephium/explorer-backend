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
import org.alephium.explorer.persistence.DBActionW
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._

trait UnconfirmedTxDao {

  def get(hash: Transaction.Hash)(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Option[UnconfirmedTransaction]]

  def list(pagination: Pagination)(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Seq[UnconfirmedTransaction]]

  def listByP2PKHAddress(address: Address)(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Seq[UnconfirmedTransaction]]

  def insertMany(utxs: Seq[UnconfirmedTransaction])(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Unit]

  def listHashes()(implicit executionContext: ExecutionContext,
                   databaseConfig: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction.Hash]]

  def removeMany(txs: Seq[Transaction.Hash])(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Unit]

  def removeAndInsertMany(toRemove: Seq[Transaction.Hash], toInsert: Seq[UnconfirmedTransaction])(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Unit]
}

object UnconfirmedTxDao extends UnconfirmedTxDao {

  def get(hash: Transaction.Hash)(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Option[UnconfirmedTransaction]] = {
    run(for {
      maybeTx <- UnconfirmedTxSchema.table.filter(_.hash === hash).result.headOption
    } yield {
      maybeTx.map(_.transaction)
    })
  }

  def list(pagination: Pagination)(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Seq[UnconfirmedTransaction]] = {
    val offset = pagination.offset.toLong
    val limit  = pagination.limit.toLong
    val toDrop = offset * limit
    run(for {
      txs <- UnconfirmedTxSchema.table.sortBy(_.lastSeen.desc).drop(toDrop).take(limit).result
    } yield {
      txs.map(_.transaction)
    })
  }

  def listByP2PKHAddress(address: Address)(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Seq[UnconfirmedTransaction]] = {
    run(for {
      txHashes <- UInputSchema.table
        .filter(_.p2pkhAddress === address)
        .map(_.txHash)
        .distinct
        .result
      txs <- UnconfirmedTxSchema.table.filter(_.hash inSet txHashes).sortBy(_.lastSeen.desc).result
    } yield {
      txs.map(_.transaction)
    })
  }

  private def insertManyAction(utxs: Seq[UnconfirmedTransaction])(
      implicit executionContext: ExecutionContext): DBActionW[Unit] = {
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

  def insertMany(utxs: Seq[UnconfirmedTransaction])(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Unit] = {
    run(insertManyAction(utxs))
  }

  def listHashes()(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction.Hash]] = {
    run(UnconfirmedTxSchema.table.map(_.hash).result)
  }

  private def removeManyAction(txs: Seq[Transaction.Hash])(
      implicit executionContext: ExecutionContext): DBActionW[Unit] = {
    for {
      _ <- UnconfirmedTxSchema.table.filter(_.hash inSet txs).delete
      _ <- UOutputSchema.table.filter(_.txHash inSet txs).delete
      _ <- UInputSchema.table.filter(_.txHash inSet txs).delete
    } yield ()
  }

  def removeMany(txs: Seq[Transaction.Hash])(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Unit] = {
    run(removeManyAction(txs))
  }

  def removeAndInsertMany(toRemove: Seq[Transaction.Hash], toInsert: Seq[UnconfirmedTransaction])(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Unit] = {
    run((for {
      _ <- removeManyAction(toRemove)
      _ <- insertManyAction(toInsert)
    } yield ()).transactionally)
  }
}
