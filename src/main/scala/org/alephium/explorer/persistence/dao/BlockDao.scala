package org.alephium.explorer.persistence.dao

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import org.alephium.explorer.{sideEffect, AnyOps}
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height, TimeInterval}
import org.alephium.explorer.persistence.{DBActionR, DBRunner}
import org.alephium.explorer.persistence.model.BlockHeader
import org.alephium.explorer.persistence.queries.TransactionQueries
import org.alephium.explorer.persistence.schema._
import org.alephium.util.AVector

trait BlockDao {
  def get(hash: BlockEntry.Hash): Future[Option[BlockEntry]]
  def insert(block: BlockEntry): Future[Unit]
  def list(timeInterval: TimeInterval): Future[Seq[BlockEntry]]
  def maxHeight(fromGroup: GroupIndex, toGroup: GroupIndex): Future[Option[Height]]
}

object BlockDao {
  def apply(config: DatabaseConfig[JdbcProfile])(
      implicit executionContext: ExecutionContext): BlockDao =
    new Impl(config)

  class Impl(val config: DatabaseConfig[JdbcProfile])(
      implicit val executionContext: ExecutionContext)
      extends BlockDao
      with BlockHeaderSchema
      with BlockDepsSchema
      with TransactionQueries
      with DBRunner {
    import config.profile.api._

    private def buildBlockEntryAction(blockHeader: BlockHeader): DBActionR[BlockEntry] =
      for {
        deps <- blockDepsTable.filter(_.hash === blockHeader.hash).map(_.dep).result
        txs  <- listTransactionsAction(blockHeader.hash)
      } yield blockHeader.toApi(AVector.from(deps), AVector.from(txs))

    private def getBlockEntryAction(hash: BlockEntry.Hash): DBActionR[Option[BlockEntry]] =
      for {
        headers <- blockHeadersTable.filter(_.hash === hash).result
        blocks  <- DBIOAction.sequence(headers.map(buildBlockEntryAction))
      } yield blocks.headOption

    def get(hash: BlockEntry.Hash): Future[Option[BlockEntry]] =
      run(getBlockEntryAction(hash))

    def insert(block: BlockEntry): Future[Unit] =
      run(
        (blockHeadersTable += BlockHeader.fromApi(block)) >>
          (blockDepsTable ++= block.deps.toArray.map(dep => (block.hash, dep))) >>
          DBIO.sequence(block.transactions.toIterable.map(insertTransactionQuery(_, block.hash)))
      ).map(_ => ())

    def list(timeInterval: TimeInterval): Future[Seq[BlockEntry]] = {
      val action =
        for {
          headers <- blockHeadersTable
            .filter(header =>
              header.timestamp >= timeInterval.from.millis && header.timestamp <= timeInterval.to.millis)
            .sortBy(_.timestamp.asc)
            .result
          blocks <- DBIOAction.sequence(headers.map(buildBlockEntryAction))
        } yield blocks

      run(action)
    }

    def maxHeight(fromGroup: GroupIndex, toGroup: GroupIndex): Future[Option[Height]] = {
      val query =
        blockHeadersTable
          .filter(header => header.chainFrom === fromGroup && header.chainTo === toGroup)
          .sortBy(_.height.desc)
          .map(_.height)

      run(query.result.headOption)
    }

    //TODO Look for something like https://flywaydb.org/ to manage schemas
    @SuppressWarnings(
      Array("org.wartremover.warts.JavaSerializable",
            "org.wartremover.warts.Product",
            "org.wartremover.warts.Serializable"))
    private val myTables =
      Seq(blockHeadersTable, blockDepsTable, transactionsTable, inputsTable, outputsTable)
    private val existing = run(MTable.getTables)
    private val f = existing.flatMap { tables =>
      Future.sequence(myTables.map { myTable =>
        val createIfNotExist =
          if (!tables.exists(_.name.name === myTable.baseTableRow.tableName)) {
            myTable.schema.create
          } else {
            DBIOAction.successful(())
          }
        run(createIfNotExist)
      })
    }
    sideEffect {
      Await.result(f, Duration.Inf)
    }
  }
}
