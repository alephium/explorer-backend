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

import scala.concurrent.ExecutionContext

import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.meta.MTable
import slick.lifted.AbstractTable

import org.alephium.explorer.persistence.schema._

/**
  * Implements functions returning [[DBAction]]s for initialising and managing database schema without
  *  executing them (no side-effects).
  */
object DBInitializer_V2 extends StrictLogging {

  @SuppressWarnings(
    Array("org.wartremover.warts.JavaSerializable",
          "org.wartremover.warts.Product",
          "org.wartremover.warts.Serializable"))
  private val allTables =
    Seq(
      BlockHeaderSchema.table,
      BlockDepsSchema.table,
      TransactionSchema.table,
      InputSchema.table,
      OutputSchema.table,
      UnconfirmedTxSchema.table,
      UInputSchema.table,
      UOutputSchema.table,
      LatestBlockSchema.table,
      HashrateSchema.table,
      TokenSupplySchema.table,
      TransactionPerAddressSchema.table
    )

  /** Given a table returns an action that will create its indexes */
  def createIndex(table: TableQuery[_]) = {
    val tableName = table.asInstanceOf[TableQuery[Table[_]]].baseTableRow.tableName

    if (tableName == BlockHeaderSchema.table.baseTableRow.tableName) {
      BlockHeaderSchema.createBlockHeadersIndexesSQL()
    } else if (tableName == TransactionSchema.table.baseTableRow.tableName) {
      BlockHeaderSchema.createBlockHeadersIndexesSQL()
    } else if (tableName == InputSchema.table.baseTableRow.tableName) {
      BlockHeaderSchema.createBlockHeadersIndexesSQL()
    } else if (tableName == OutputSchema.table.baseTableRow.tableName) {
      BlockHeaderSchema.createBlockHeadersIndexesSQL()
    } else if (tableName == TransactionPerAddressSchema.table.baseTableRow.tableName) {
      BlockHeaderSchema.createBlockHeadersIndexesSQL()
    } else {
      DBIO.successful(0)
    }
  }

  def dropAllTables() =
    dropTables(allTables)

  def createAllTables()(implicit ec: ExecutionContext) =
    createTables(allTables)

  def createIndexes(tables: Iterable[TableQuery[_]]) =
    DBIO.sequence(tables.map(createIndex))

  def createTable[T <: AbstractTable[_]](table: TableQuery[T])(implicit ec: ExecutionContext) =
    createTables(Iterable(table))

  /**
    * Creates missing table
    *
    * @param tablesToCreate Tables to create if not exists.
    * @param                ec Application level [[ExecutionContext]]
    *
    * @return               An [[DBAction]] to create the missing tables.
    */
  def createTables(tablesToCreate: Iterable[TableQuery[_]])(implicit ec: ExecutionContext) =
    MTable.getTables flatMap { existingTables =>
      val existingTableNames = existingTables.map(_.name.name)

      val missingTables =
        tablesToCreate
          .filterNot { tableToCreate =>
            val tableToCreateName =
              tableToCreate
                .asInstanceOf[TableQuery[Table[_]]]
                .baseTableRow
                .tableName

            existingTableNames.contains(tableToCreateName)
          }

      val createMissingTables =
        missingTables.map(_.asInstanceOf[TableQuery[Table[_]]].schema.create)

      DBIO
        .sequence(createMissingTables)
        .andThen(createIndexes(missingTables))
        .transactionally
    }

  def dropTable[T <: AbstractTable[_]](table: TableQuery[T]) =
    sqlu"DROP TABLE IF EXISTS #${table.baseTableRow.tableName}"

  def dropTables(tables: Iterable[TableQuery[_]]) =
    DBIO.sequence(tables.asInstanceOf[Iterable[TableQuery[Table[_]]]].map(dropTable))

}
