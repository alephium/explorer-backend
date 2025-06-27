// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.api.model.IntervalType
import org.alephium.explorer.persistence.model.TransactionHistoryEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.GroupIndex
import org.alephium.util.TimeStamp

object TransactionHistorySchema extends Schema[TransactionHistoryEntity]("transactions_history") {

  class TransactionsHistories(tag: Tag) extends Table[TransactionHistoryEntity](tag, name) {
    def timestamp: Rep[TimeStamp]       = column[TimeStamp]("timestamp")
    def chainFrom: Rep[GroupIndex]      = column[GroupIndex]("chain_from")
    def chainTo: Rep[GroupIndex]        = column[GroupIndex]("chain_to")
    def count: Rep[Long]                = column[Long]("value")
    def intervalType: Rep[IntervalType] = column[IntervalType]("interval_type")

    def pk: PrimaryKey =
      primaryKey("transactions_history_pk", (intervalType, timestamp, chainFrom, chainTo))
    def intervalTypeIdx: Index = index("transactions_history_interval_type_idx", intervalType)
    def timestampIdx: Index    = index("transactions_history_timestamp_idx", timestamp)

    def * : ProvenShape[TransactionHistoryEntity] =
      (timestamp, chainFrom, chainTo, count, intervalType)
        .<>((TransactionHistoryEntity.apply _).tupled, TransactionHistoryEntity.unapply)
  }

  val table: TableQuery[TransactionsHistories] = TableQuery[TransactionsHistories]
}
