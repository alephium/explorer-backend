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

package org.alephium.explorer.persistence.model

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

import akka.util.ByteString
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.persistence.DBActionR
import org.alephium.explorer.util.SlickUtil._
import org.alephium.serde._
import org.alephium.util.TimeStamp

/** Values of AppState */
sealed trait AppState

/** Keys for AppState */
sealed trait AppStateKey[V <: AppState] {
  def key: String

  def apply(bytes: ByteString): Either[SerdeError, AppState]

  final def get()(implicit
      ct: ClassTag[V],
      gr: GetResult[V],
      ec: ExecutionContext
  ): DBActionR[Option[V]] =
    sql"""
      SELECT value
      FROM app_state
      WHERE key = $key
      LIMIT 1
      """.asAS[V].headOrNone
}

object AppState {

  /** Fetch value for the key and serialised value, or-else, throw error */
  def applyOrThrow(key: AppStateKey[_], bytes: ByteString): AppState =
    key(bytes) match {
      case Left(error)     => throw error
      case Right(appState) => appState
    }

  def unapplyOpt(result: AppState): Option[(AppStateKey[_], ByteString)] =
    Some(unapply(result))

  def unapply(result: AppState): (AppStateKey[_], ByteString) =
    result match {
      case LastFinalizedInputTime(value) => (LastFinalizedInputTime, serialize(value))
      case MigrationVersion(value)       => (MigrationVersion, serialize(value))
      case LastHoldersUpdate(value)      => (LastHoldersUpdate, serialize(value))
    }

  object LastFinalizedInputTime extends AppStateKey[LastFinalizedInputTime] {
    val key = "last_finalized_input_time"

    def apply(bytes: ByteString): Either[SerdeError, LastFinalizedInputTime] =
      deserialize[TimeStamp](bytes).map(LastFinalizedInputTime(_))
  }

  final case class LastFinalizedInputTime(time: TimeStamp) extends AppState

  object MigrationVersion extends AppStateKey[MigrationVersion] {
    val key = "migrations_version"

    def apply(bytes: ByteString): Either[SerdeError, MigrationVersion] =
      deserialize[Int](bytes).map(MigrationVersion(_))
  }

  final case class MigrationVersion(version: Int) extends AppState

  object LastHoldersUpdate extends AppStateKey[LastHoldersUpdate] {
    val key = "last_holders_update"

    def apply(bytes: ByteString): Either[SerdeError, LastHoldersUpdate] =
      deserialize[TimeStamp](bytes).map(LastHoldersUpdate(_))
  }

  final case class LastHoldersUpdate(time: TimeStamp) extends AppState

  /** All the keys */
  def keys(): Array[AppStateKey[_]] =
    Array(LastFinalizedInputTime, MigrationVersion, LastHoldersUpdate)

}
