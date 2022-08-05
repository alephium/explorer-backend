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

import akka.util.ByteString

import org.alephium.serde._
import org.alephium.util.TimeStamp

/** Values of AppState */
sealed trait AppState

/** Keys for AppState */
sealed trait AppStateKey {
  def key: String

  def apply(bytes: ByteString): Either[SerdeError, AppState]
}

object AppState {

  /** Fetch value for the key and serialised value, or-else, throw error */
  def applyOrThrow(key: AppStateKey, bytes: ByteString): AppState =
    key(bytes) match {
      case Left(error)     => throw error
      case Right(appState) => appState
    }

  def unapplyOpt(result: AppState): Option[(AppStateKey, ByteString)] =
    Some(unapply(result))

  def unapply(result: AppState): (AppStateKey, ByteString) =
    result match {
      case LastFinalizedInputTime(value) => (LastFinalizedInputTime, serialize(value))
      case MigrationVersion(value)       => (MigrationVersion, serialize(value))
    }

  object LastFinalizedInputTime extends AppStateKey {
    val key = "last_finalized_input_time"

    def apply(bytes: ByteString): Either[SerdeError, LastFinalizedInputTime] =
      deserialize[TimeStamp](bytes).map(LastFinalizedInputTime(_))
  }

  final case class LastFinalizedInputTime(time: TimeStamp) extends AppState

  object MigrationVersion extends AppStateKey {
    val key = "migrations_version"

    def apply(bytes: ByteString): Either[SerdeError, MigrationVersion] =
      deserialize[Int](bytes).map(MigrationVersion(_))
  }

  final case class MigrationVersion(version: Int) extends AppState

  /** All the keys */
  def keys(): Array[AppStateKey] =
    Array(LastFinalizedInputTime, MigrationVersion)

}
