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

package org.alephium.explorer.persistence.queries

import scala.reflect.ClassTag

import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenCoreProtocol.hashGen
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model.{AppState, AppStateKey}
import org.alephium.explorer.persistence.schema.AppStateSchema
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.serde._

class AppStateQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with DBRunner {

  "insert" should {
    "write LastFinalizedInputTime key-value" in {
      forAll(timestampGen) { timestamp =>
        run(AppStateSchema.table.delete).futureValue
        val insertValue = AppState.LastFinalizedInputTime(timestamp)
        run(AppStateQueries.insert(insertValue)).futureValue is 1
        run(AppStateQueries.get(AppState.LastFinalizedInputTime)).futureValue is Some(insertValue)
      }
    }

    "write MigrationVersion key-value" in {
      forAll { (int: Int) =>
        run(AppStateSchema.table.delete).futureValue
        val insertValue = AppState.MigrationVersion(int)
        run(AppStateQueries.insert(insertValue)).futureValue is 1
        run(AppStateQueries.get(AppState.MigrationVersion)).futureValue is Some(insertValue)
      }
    }
  }

  "insertOrUpdate" should {
    "overwrite key-values" in {
      forAll { (int: Int) =>
        val insertValue = AppState.MigrationVersion(int)
        run(AppStateQueries.insertOrUpdate(insertValue)).futureValue is 1
        run(AppStateQueries.get(AppState.MigrationVersion)).futureValue is Some(insertValue)
      }
    }
  }

  "get" should {
    def checkFailure[V <: AppState: ClassTag: GetResult](key: AppStateKey[V]) = {
      forAll(hashGen) { hash =>
        run(AppStateSchema.table.delete).futureValue
        run(sqlu"INSERT INTO app_state VALUES (${key.key}, ${serialize(hash)})").futureValue
        run(AppStateQueries.get(key)).failed.futureValue should (be(
          a[SerdeError.WrongFormat]
        ) or be(a[org.postgresql.util.PSQLException]))
      }
    }

    "fail to read corrupted MigrationVersion" in {
      checkFailure(AppState.MigrationVersion)
    }

    "fail to read corrupted LastFinalizedInputTime" in {
      checkFailure(AppState.LastFinalizedInputTime)
    }
  }
}
