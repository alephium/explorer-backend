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

package org.alephium.explorer.persistence

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import org.alephium.explorer.AnyOps
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.schema._

class DBInitializer(val config: DatabaseConfig[JdbcProfile])(
    implicit val executionContext: ExecutionContext)
    extends BlockHeaderSchema
    with BlockDepsSchema
    with TransactionSchema
    with InputSchema
    with OutputSchema
    with UnconfirmedTxSchema
    with UInputSchema
    with UOutputSchema
    with LatestBlockSchema
    with TokenSupplySchema
    with HashrateSchema
    with TransactionPerAddressSchema
    with DBRunner
    with StrictLogging {
  import config.profile.api._

  @SuppressWarnings(
    Array("org.wartremover.warts.JavaSerializable",
          "org.wartremover.warts.Product",
          "org.wartremover.warts.Serializable"))
  private val allTables =
    Seq(
      blockHeadersTable,
      blockDepsTable,
      transactionsTable,
      inputsTable,
      outputsTable,
      unconfirmedTxsTable,
      uinputsTable,
      uoutputsTable,
      latestBlocksTable,
      hashrateTable,
      tokenSupplyTable,
      transactionPerAddressesTable
    )

  def createTables(): Future[Unit] = {
    //TODO Look for something like https://flywaydb.org/ to manage schemas
    val existingTables = run(MTable.getTables)
    existingTables
      .flatMap { tables =>
        Future.sequence(allTables.map { table =>
          val createIfNotExist =
            if (!tables.exists(_.name.name === table.baseTableRow.tableName)) {
              table.schema.create
            } else {
              DBIOAction.successful(())
            }
          run(createIfNotExist)
        })
      }
      .flatMap(_ => createIndexes())
  }

  private def createIndexes(): Future[Unit] = {
    run(for {
      _ <- createBlockHeadersIndexesSQL()
      _ <- createTransactionMainChainIndex()
      _ <- createInputMainChainIndex()
      _ <- createOutputMainChainIndex()
      _ <- createHashrateIntervalTypeIndex()
      _ <- createTransactionPerAddressMainChainIndex()
    } yield ())
  }

  def dropTables(): Future[Unit] = {
    val query = allTables
      .map { table =>
        val name = table.baseTableRow.tableName
        s"DROP TABLE IF EXISTS $name;"
      }
      .mkString("\n")
    run(sqlu"#$query").map(_ => ())
  }
}

object DBInitializer {
  def apply(config: DatabaseConfig[JdbcProfile])(
      implicit executionContext: ExecutionContext): DBInitializer = new DBInitializer(config)
}
