// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.persistence.{DBActionR, DBActionW}
import org.alephium.explorer.persistence.model.{AppState, AppStateKey}
import org.alephium.explorer.persistence.schema.AppStateSchema

/** Queries for table [[org.alephium.explorer.persistence.schema.AppStateSchema.table]] */
object AppStateQueries {

  @inline def insert(appState: AppState): DBActionW[Int] =
    AppStateSchema.table += appState

  @inline def insertOrUpdate(appState: AppState): DBActionW[Int] =
    AppStateSchema.table insertOrUpdate appState

  @inline def get[V <: AppState: ClassTag: GetResult](appState: AppStateKey[V])(implicit
      ec: ExecutionContext
  ): DBActionR[Option[V]] =
    appState.get()
}
