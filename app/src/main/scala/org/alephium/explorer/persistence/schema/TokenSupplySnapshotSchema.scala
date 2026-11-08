// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{PrimaryKey, ProvenShape}

import org.alephium.explorer.persistence.model.TokenSupplySnapshotEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.TokenId
import org.alephium.util.{TimeStamp, U256}

object TokenSupplySnapshotSchema
    extends Schema[TokenSupplySnapshotEntity]("token_supply_snapshots") {
  class Snapshots(tag: Tag) extends Table[TokenSupplySnapshotEntity](tag, name) {
    def token: Rep[TokenId]       = column[TokenId]("token")
    def timestamp: Rep[TimeStamp] = column[TimeStamp]("block_timestamp")
    def total: Rep[U256]          = column[U256]("total", O.SqlType("DECIMAL(80,0)"))
    def pk: PrimaryKey            = primaryKey("token_supply_snapshots_pk", (token, timestamp))
    def * : ProvenShape[TokenSupplySnapshotEntity] =
      (token, timestamp, total).<>(
        (TokenSupplySnapshotEntity.apply _).tupled,
        TokenSupplySnapshotEntity.unapply
      )
  }
  val table: TableQuery[Snapshots] = TableQuery[Snapshots]
}
