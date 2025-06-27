// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.api.model.IntervalType
import org.alephium.explorer.persistence.model.HashrateEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.util.TimeStamp

object HashrateSchema extends Schema[HashrateEntity]("hashrates") {

  class Hashrates(tag: Tag) extends Table[HashrateEntity](tag, name) {
    def timestamp: Rep[TimeStamp]       = column[TimeStamp]("block_timestamp")
    def value: Rep[BigDecimal]          = column[BigDecimal]("value")
    def intervalType: Rep[IntervalType] = column[IntervalType]("interval_type")

    def pk: PrimaryKey = primaryKey("hashrates_pk", (timestamp, intervalType))

    def timestampIdx: Index    = index("hashrates_timestamp_idx", timestamp)
    def intervalTypeIdx: Index = index("hashrates_interval_type_idx", intervalType)

    def * : ProvenShape[HashrateEntity] =
      (timestamp, value, intervalType)
        .<>((HashrateEntity.apply _).tupled, HashrateEntity.unapply)
  }

  val table: TableQuery[Hashrates] = TableQuery[Hashrates]
}
