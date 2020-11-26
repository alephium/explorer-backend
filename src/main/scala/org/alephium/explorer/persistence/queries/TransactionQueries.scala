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

package org.alephium.explorer.persistence.queries

import scala.concurrent.ExecutionContext

import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import org.alephium.explorer.api.model.{Address, BlockEntry, Transaction}
import org.alephium.explorer.persistence.{DBActionR, DBActionRW, DBActionW}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema.{InputSchema, OutputSchema, TransactionSchema}
import org.alephium.util.{AVector, TimeStamp}

trait TransactionQueries extends TransactionSchema with InputSchema with OutputSchema {

  implicit def executionContext: ExecutionContext
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  def insertTransactionFromBlockQuery(blockEntity: BlockEntity): DBActionW[Unit] =
    ((transactionsTable ++= blockEntity.transactions.toArray) >>
      (inputsTable ++= blockEntity.inputs.toArray) >>
      (outputsTable ++= blockEntity.outputs.toArray))
      .map(_ => ())

  def listTransactionsAction(blockHash: BlockEntry.Hash): DBActionR[Seq[Transaction]] =
    transactionsTable
      .filter(_.blockHash === blockHash)
      .sortBy(_.timestamp.asc)
      .result
      .flatMap(hashes => DBIOAction.sequence(hashes.map(getKnownTransactionFromEntityAction)))

  def getTransactionAction(txHash: Transaction.Hash): DBActionR[Option[Transaction]] =
    transactionsTable.filter(_.hash === txHash).result.headOption.flatMap {
      case None     => DBIOAction.successful(None)
      case Some(tx) => getKnownTransactionFromEntityAction(tx).map(Some.apply)
    }

  def getTransactionsByAddress(address: Address, txLimit: Int): DBActionR[Seq[Transaction]] = {
    for {
      txHashes <- outputsTable
        .filter(_.address === address)
        .map(out => (out.txHash, out.timestamp))
        .distinct
        .sortBy { case (_, timestamp) => timestamp.asc }
        .take(txLimit)
        .result
      txs <- DBIOAction.sequence(txHashes.map {
        case (txHash, timestamp) => getKnownTransactionAction(txHash, timestamp)
      })
    } yield txs
  }

  private def getKnownTransactionFromEntityAction(tx: TransactionEntity): DBActionR[Transaction] =
    getKnownTransactionAction(tx.hash, tx.timestamp)

  private def getKnownTransactionAction(txHash: Transaction.Hash,
                                        timestamp: TimeStamp): DBActionR[Transaction] =
    for {
      ins        <- inputsTable.filter(_.txHash === txHash).result
      outs       <- outputsTable.filter(_.txHash === txHash).result
      insOutsRef <- DBIOAction.sequence(ins.map(getOutputFromInput))
    } yield
      Transaction(txHash,
                  timestamp,
                  AVector.from(insOutsRef.map { case (in, out) => in.toApi(out) }),
                  AVector.from(outs.map(_.toApi)))

  private def getOutputFromInput(
      input: InputEntity): DBActionR[(InputEntity, Option[OutputEntity])] =
    outputsTable
      .filter(_.outputRefKey === input.key)
      .result
      .headOption
      .map(output => (input, output))

  def updateSpentAction(): DBActionRW[Int] = {
    outputsTable
      .filter(_.spent.isEmpty)
      .join(inputsTable)
      .on(_.outputRefKey === _.key)
      .map { case (out, in) => (out, in.txHash) }
      .result
      .map { res =>
        DBIOAction.sequence(res.map {
          case (out, txHash) =>
            outputsTable
              .filter(_.outputRefKey === out.outputRefKey)
              .map(_.spent)
              .update(Some(txHash))
        })
      }
  }.flatMap(_.map(_.sum))

  def getBalanceAction(address: Address): DBActionR[Double] =
    outputsTable
      .filter(out => out.address === address && out.spent.isEmpty)
      .map(_.amount)
      .sum
      .result
      .map(_.getOrElse(0.0))
}
