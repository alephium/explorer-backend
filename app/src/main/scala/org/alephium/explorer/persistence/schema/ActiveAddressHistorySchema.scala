// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.api.model.IntervalType
import org.alephium.explorer.persistence.model.ActiveAddressHistoryEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.util.TimeStamp

object ActiveAddressHistorySchema
    extends Schema[ActiveAddressHistoryEntity]("active_address_history") {

  class ActiveAddressHistories(tag: Tag) extends Table[ActiveAddressHistoryEntity](tag, name) {
    def timestamp: Rep[TimeStamp]       = column[TimeStamp]("timestamp")
    def count: Rep[Long]                = column[Long]("value")
    def intervalType: Rep[IntervalType] = column[IntervalType]("interval_type")

    def pk: PrimaryKey =
      primaryKey("active_address_history_pk", (intervalType, timestamp))
    def intervalTypeIdx: Index = index("active_address_history_interval_type_idx", intervalType)
    def timestampIdx: Index    = index("active_address_history_timestamp_idx", timestamp)

    def * : ProvenShape[ActiveAddressHistoryEntity] =
      (timestamp, count, intervalType)
        .<>((ActiveAddressHistoryEntity.apply _).tupled, ActiveAddressHistoryEntity.unapply)
  }

  val table: TableQuery[ActiveAddressHistories] = TableQuery[ActiveAddressHistories]
}
