package org.alephium.explorer.persistence.queries

import scala.concurrent.ExecutionContext

import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.Transaction
import org.alephium.explorer.persistence.{DBActionR, DBActionW}
import org.alephium.explorer.persistence.model.{InputEntity, OutputEntity, TransactionEntity}
import org.alephium.explorer.persistence.schema.{InputSchema, OutputSchema, TransactionSchema}
import org.alephium.util.AVector

trait TransactionQueries extends TransactionSchema with InputSchema with OutputSchema {

  implicit def executionContext: ExecutionContext
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  def insertTransactionQuery(transaction: Transaction, blockHash: Hash): DBActionW[Transaction] =
    ((transactionsTable += TransactionEntity(transaction.hash, blockHash)) >>
      (inputsTable ++= transaction.inputs.map(InputEntity.fromApi(_, transaction.hash)).toArray) >>
      (outputsTable ++= transaction.outputs.map(OutputEntity.fromApi(_, transaction.hash)).toArray))
      .map(_ => transaction)

  def listTransactionsAction(blockHash: Hash): DBActionR[Seq[Transaction]] =
    transactionsTable
      .filter(_.blockHash === blockHash)
      .map(_.hash)
      .result
      .flatMap(hashes => DBIOAction.sequence(hashes.map(getTransactionAction)))

  private def getTransactionAction(txHash: Hash): DBActionR[Transaction] =
    for {
      ins  <- inputsTable.filter(_.txHash === txHash).result
      outs <- outputsTable.filter(_.txHash === txHash).result
    } yield Transaction(txHash, AVector.from(ins.map(_.toApi)), AVector.from(outs.map(_.toApi)))
}
