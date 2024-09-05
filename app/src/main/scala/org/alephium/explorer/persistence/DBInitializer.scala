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

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.meta.MTable

import org.alephium.explorer.AnyOps
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.schema._

object DBInitializer extends StrictLogging {

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.JavaSerializable",
      "org.wartremover.warts.Product",
      "org.wartremover.warts.Serializable",
      "org.wartremover.warts.PublicInference"
    )
  )
  val allTables =
    ArraySeq(
      BlockHeaderSchema.table,
      TransactionSchema.table,
      InputSchema.table,
      OutputSchema.table,
      TokenOutputSchema.table,
      MempoolTransactionSchema.table,
      UInputSchema.table,
      UOutputSchema.table,
      LatestBlockSchema.table,
      HashrateSchema.table,
      TokenSupplySchema.table,
      TransactionPerAddressSchema.table,
      TransactionPerTokenSchema.table,
      TokenPerAddressSchema.table,
      TokenInfoSchema.table,
      TransactionHistorySchema.table,
      EventSchema.table,
      HolderSchema.table,
      TokenHolderSchema.table,
      ContractSchema.table,
      AppStateSchema.table,
      FungibleTokenMetadataSchema.table,
      NFTMetadataSchema.table,
      NFTCollectionMetadataSchema.table
    )

  def initialize()(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    for {
      _ <- createTables()
      _ <- Migrations.migrate(databaseConfig)
      _ <- createIndexes()
    } yield ()
  }

  private def createTables()(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    logger.info("Create Tables")
    // TODO Look for something like https://flywaydb.org/ to manage schemas
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
      .map(_ => ())
  }

  private def createIndexes()(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    logger.info("Create Indexes")
    run(for {
      _ <- BlockHeaderSchema.createBlockHeadersIndexes()
      _ <- TransactionSchema.createMainChainIndex()
      _ <- InputSchema.createMainChainIndex()
      _ <- OutputSchema.createMainChainIndex()
      _ <- TransactionPerAddressSchema.createIndexes()
      _ <- OutputSchema.createNonSpentIndex()
      _ <- InputSchema.createOutputRefAddressNullIndex()
      _ <- TransactionPerTokenSchema.createIndexes()
      _ <- TokenPerAddressSchema.createIndexes()
    } yield ())
  }

  def dropTables()(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    val query = allTables
      .map { table =>
        val name = table.baseTableRow.tableName
        s"DROP TABLE IF EXISTS $name;"
      }
      .mkString("\n")
    run(sqlu"#$query").map(_ => ())
  }
}
