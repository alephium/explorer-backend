// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.util._

object TimeStampTableFixture {

  def ts(str: String): TimeStamp = {
    TimeStamp.unsafe(java.time.Instant.parse(str).toEpochMilli)
  }

  class TimeStamps(tag: Tag) extends Table[TimeStamp](tag, "timestamps") {
    def timestamp: Rep[TimeStamp]  = column[TimeStamp]("block_timestamp")
    def * : ProvenShape[TimeStamp] = timestamp
  }

  val timestampTable: TableQuery[TimeStamps] = TableQuery[TimeStamps]
}
