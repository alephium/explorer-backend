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

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model.AppState
import org.alephium.explorer.persistence.schema.AppStateSchema

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
      forAll { int: Int =>
        run(AppStateSchema.table.delete).futureValue
        val insertValue = AppState.MigrationVersion(int)
        run(AppStateQueries.insert(insertValue)).futureValue is 1
        run(AppStateQueries.get(AppState.MigrationVersion)).futureValue is Some(insertValue)
      }
    }
  }

  "insertOrUpdate" should {
    "overwrite key-values" in {
      forAll { int: Int =>
        val insertValue = AppState.MigrationVersion(int)
        run(AppStateQueries.insertOrUpdate(insertValue)).futureValue is 1
        run(AppStateQueries.get(AppState.MigrationVersion)).futureValue is Some(insertValue)
      }
    }
  }
}
