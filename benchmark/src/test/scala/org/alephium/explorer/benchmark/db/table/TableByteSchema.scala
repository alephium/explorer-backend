// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db.table

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.lifted.ProvenShape

trait TableByteSchema {

  val config: DatabaseConfig[PostgresProfile]

  import config.profile.api._

  /** Table with single column that stores byte arrays
    */
  class TableBytea(tag: Tag) extends Table[Array[Byte]](tag, "table_bytea") {
    def hash: Rep[Array[Byte]] = column[Array[Byte]]("hash", O.PrimaryKey, O.SqlType("BYTEA"))

    def * : ProvenShape[Array[Byte]] = hash
  }

  val tableByteaQuery: TableQuery[TableBytea] = TableQuery[TableBytea]

}
