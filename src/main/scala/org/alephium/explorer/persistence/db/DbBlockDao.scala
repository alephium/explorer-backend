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
import org.alephium.explorer.persistence.model.BlockHeader
import org.alephium.explorer.persistence.schema.{BlockDepsSchema, BlockHeaderSchema}
import org.alephium.util.AVector

class DbBlockDao(val config: DatabaseConfig[JdbcProfile])(
    implicit executionContext: ExecutionContext)
    extends BlockDao
    with BlockHeaderSchema
    with BlockDepsSchema {
  import config.profile.api._

  def get(id: String): Future[Option[BlockEntry]] = {
    val query = for {
      (header, blockDep) <- blockHeaders
        .filter(_.hash === id)
        .joinLeft(blockDeps)
        .on(_.hash === _.hash)
    } yield (header, blockDep.map(_.dep))

    config.db.run(query.result).map { result =>
      result.headOption.map {
        case (blockHeader, _) =>
          blockHeader.toApi(AVector.from(result.flatMap { case (_, dep) => dep }))
      }
    }
  }

  def insert(block: BlockEntry): Future[Either[String, BlockEntry]] = {
    config.db.run(
      (blockHeaders += BlockHeader.fromApi(block)) >>
        (blockDeps ++= block.deps.toArray.map(dep => (block.hash, dep)))
          .map(_ => Right(block))
    )
  }

  //TODO Look for something like https://flywaydb.org/ to manage schemas
  @SuppressWarnings(
    Array("org.wartremover.warts.JavaSerializable",
          "org.wartremover.warts.Product",
          "org.wartremover.warts.Serializable"))
  private val myTables = Seq(blockHeaders, blockDeps)
  private val existing = config.db.run(MTable.getTables)
  private val f = existing.flatMap { tables =>
    Future.sequence(myTables.map { myTable =>
      val createIfNotExist =
        if (!tables.exists(_.name.name === myTable.baseTableRow.tableName)) {
          myTable.schema.create
        } else {
          DBIOAction.successful(())
        }
      config.db.run(createIfNotExist)
    })
  }
  sideEffect {
    Await.result(f, Duration.Inf)
  }
}
