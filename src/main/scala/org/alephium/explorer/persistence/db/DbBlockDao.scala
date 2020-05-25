package org.alephium.explorer.persistence.db

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import org.alephium.explorer.{sideEffect, AnyOps}
import org.alephium.explorer.api.model.BlockEntry
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model.Block
import org.alephium.explorer.persistence.schema.BlockSchema

class DbBlockDao(val config: DatabaseConfig[JdbcProfile])(
    implicit executionContext: ExecutionContext)
    extends BlockDao
    with BlockSchema {
  import config.profile.api._

  def get(id: String): Future[Option[BlockEntry]] =
    config.db.run(blocks.filter(_.hash === id).result.map(_.headOption.map(_.toApi)))

  def insert(block: BlockEntry): Future[Either[String, BlockEntry]] =
    config.db.run(blocks += Block.fromApi(block)).map(_ => Right(block))

  //TODO Look for something like https://flywaydb.org/ to manage schemas
  private val existing = config.db.run(MTable.getTables)
  private val f = existing.flatMap { tables =>
    val createIfNotExist =
      if (!tables.exists(_.name.name === blocks.baseTableRow.tableName)) {
        blocks.schema.create
      } else {
        DBIOAction.successful(())
      }
    config.db.run(createIfNotExist)
  }
  sideEffect {
    Await.result(f, Duration.Inf)
  }
}
