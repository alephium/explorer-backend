// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db.table

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.lifted.ProvenShape

trait TableVarcharSchema {

  val config: DatabaseConfig[PostgresProfile]

  import config.profile.api._

  /** Table with single column that stores String values
    */
  class TableVarchar(tag: Tag) extends Table[String](tag, "table_varchar") {
    def hash: Rep[String] = column[String]("hash", O.PrimaryKey)

    def * : ProvenShape[String] = hash
  }

  val tableVarcharQuery: TableQuery[TableVarchar] = TableQuery[TableVarchar]
}
