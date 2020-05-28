package org.alephium.explorer.persistence.db

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import org.alephium.explorer.{sideEffect, AnyOps}
import org.alephium.explorer.api.model.{BlockEntry, TimeInterval}
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
    val query =
      blockHeaders
        .filter(_.hash === id)

    config.db.run(joinDeps(query).result).map { result =>
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

  //TODO Optimize the query so we can do the ordering directly, the problem here is that we need
  //to groupBy the result and it screw the order of the db, so we can't use the sql `ORDER_BY`
  //we need a slick `Query[_,(BlockHeader, Seq[BlockDep), Seq]`
  def list(timeInterval: TimeInterval): Future[Seq[BlockEntry]] = {
    val query =
      blockHeaders
        .filter(header =>
          header.timestamp >= timeInterval.from.millis && header.timestamp <= timeInterval.to.millis)

    config.db.run(joinDeps(query).result).map { result =>
      result
        .groupBy {
          case (blockHeader, _) => blockHeader
        }
        .toSeq
        .map {
          case (header, deps) => header.toApi(AVector.from(deps.flatMap { case (_, dep) => dep }))
        }
        .sortBy(_.timestamp)
        .reverse
    }
  }

  private def joinDeps(headers: Query[BlockHeaders, BlockHeader, Seq]) =
    for {
      (header, blockDep) <- headers.joinLeft(blockDeps).on(_.hash === _.hash)
    } yield (header, blockDep.map(_.dep))

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
