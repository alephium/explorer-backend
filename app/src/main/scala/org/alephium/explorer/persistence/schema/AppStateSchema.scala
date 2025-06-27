// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import akka.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import org.alephium.explorer.persistence.model.{AppState, AppStateKey}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._

object AppStateSchema extends Schema[AppState]("app_state") {

  class AppStates(tag: Tag) extends Table[AppState](tag, name) {
    def key: Rep[AppStateKey[_]] = column[AppStateKey[_]]("key", O.PrimaryKey)
    def value: Rep[ByteString]   = column[ByteString]("value")

    def * : ProvenShape[AppState] =
      (key, value).<>((AppState.applyOrThrow _).tupled, AppState.unapplyOpt)
  }

  val table: TableQuery[AppStates] = TableQuery[AppStates]
}
