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

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.MempoolQueries._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{AddressLike, TransactionId}

trait MempoolDao {

  def get(hash: TransactionId)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Option[MempoolTransaction]]

  def list(pagination: Pagination)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]]

  def listByAddress(address: AddressLike)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]]

  def insertMany(utxs: ArraySeq[MempoolTransaction])(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit]

  def listHashes()(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TransactionId]]

  def removeMany(txs: ArraySeq[TransactionId])(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit]

  def removeAndInsertMany(
      toRemove: ArraySeq[TransactionId],
      toInsert: ArraySeq[MempoolTransaction]
  )(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit]
}

object MempoolDao extends MempoolDao {

  def get(hash: TransactionId)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Option[MempoolTransaction]] = {
    run(for {
      maybeTx <- utxFromTxHash(hash)
      inputs  <- uinputsFromTx(hash)
      outputs <- uoutputsFromTx(hash)
    } yield {
      maybeTx.headOption.map { tx =>
        MempoolTransaction(
          tx.hash,
          tx.chainFrom,
          tx.chainTo,
          inputs.map(_.toApi),
          outputs.map(_.toApi),
          tx.gasAmount,
          tx.gasPrice,
          tx.lastSeen
        )
      }
    })
  }
  def list(pagination: Pagination)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]] = {

    run(for {
      txs <- listPaginatedMempoolTransactionsQuery(pagination)
      txHashes = txs.map(_.hash)
      inputs  <- uinputsFromTxs(txHashes)
      outputs <- uoutputsFromTxs(txHashes)
    } yield {
      txs.map { tx =>
        MempoolTransaction(
          tx.hash,
          tx.chainFrom,
          tx.chainTo,
          inputs.filter(_.txHash == tx.hash).sortBy(_.uinputOrder).map(_.toApi),
          outputs.filter(_.txHash == tx.hash).sortBy(_.uoutputOrder).map(_.toApi),
          tx.gasAmount,
          tx.gasPrice,
          tx.lastSeen
        )
      }
    })
  }
  def listByAddress(address: AddressLike)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[MempoolTransaction]] = {
    run(for {
      txHashes <- listUTXHashesByAddress(address)
      txs      <- utxsFromTxs(txHashes)
      inputs   <- uinputsFromTxs(txHashes)
      outputs  <- uoutputsFromTxs(txHashes)
    } yield {
      txs.map { tx =>
        MempoolTransaction(
          tx.hash,
          tx.chainFrom,
          tx.chainTo,
          inputs.filter(_.txHash == tx.hash).sortBy(_.uinputOrder).map(_.toApi),
          outputs.filter(_.txHash == tx.hash).sortBy(_.uoutputOrder).map(_.toApi),
          tx.gasAmount,
          tx.gasPrice,
          tx.lastSeen
        )
      }
    })
  }

  private def insertManyAction(
      utxs: ArraySeq[MempoolTransaction]
  )(implicit executionContext: ExecutionContext): DBActionW[Unit] = {
    val entities = utxs.map(MempoolTransactionEntity.from)
    val txs      = entities.map { case (tx, _, _) => tx }
    val inputs   = entities.flatMap { case (_, in, _) => in }
    val outputs  = entities.flatMap { case (_, _, out) => out }
    for {
      _ <- DBIOAction.sequence(txs.map(MempoolTransactionSchema.table.insertOrUpdate))
      _ <- DBIOAction.sequence(inputs.map(UInputSchema.table.insertOrUpdate))
      _ <- DBIOAction.sequence(outputs.map(UOutputSchema.table.insertOrUpdate))
    } yield ()
  }

  def insertMany(utxs: ArraySeq[MempoolTransaction])(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    run(insertManyAction(utxs))
  }

  def listHashes()(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TransactionId]] = {
    run(listHashesQuery)
  }

  private def removeManyAction(
      txs: ArraySeq[TransactionId]
  )(implicit executionContext: ExecutionContext): DBActionW[Unit] = {
    for {
      _ <- MempoolTransactionSchema.table.filter(_.hash inSet txs).delete
      _ <- UOutputSchema.table.filter(_.txHash inSet txs).delete
      _ <- UInputSchema.table.filter(_.txHash inSet txs).delete
    } yield ()
  }

  def removeMany(txs: ArraySeq[TransactionId])(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    run(removeManyAction(txs))
  }

  def removeAndInsertMany(
      toRemove: ArraySeq[TransactionId],
      toInsert: ArraySeq[MempoolTransaction]
  )(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    run((for {
      _ <- removeManyAction(toRemove)
      _ <- insertManyAction(toInsert)
    } yield ()).transactionally)
  }
}
