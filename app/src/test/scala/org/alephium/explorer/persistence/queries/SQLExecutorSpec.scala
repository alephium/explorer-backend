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

import scala.util.Success

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.persistence.queries.SQLExecutor.SlickSQLExecutor
import org.alephium.explorer.utils.TestUtils._

class SQLExecutorSpec extends AlephiumSpec {

  it should "execute update queries" in new DatabaseFixture {
    using(new SlickSQLExecutor(databaseConfig)) { executor =>
      executor.runUpdate("CREATE TABLE TEST_TABLE(value varchar)") is Success(0)
      executor.runUpdate("INSERT INTO TEST_TABLE values ('value')") is Success(1)
    }
  }

  it should "return empty select on empty table" in new DatabaseFixture {
    using(new SlickSQLExecutor(databaseConfig)) { executor =>
      executor.runUpdate("CREATE TABLE TEST_TABLE(key varchar)") is Success(0)
      executor.runSelect[String, List]("SELECT * FROM TEST_TABLE")(_.getString("key")) is
        Success(List.empty)
    }
  }

  it should "return non-empty select on non-empty table" in new DatabaseFixture {
    using(new SlickSQLExecutor(databaseConfig)) { executor =>
      executor.runUpdate("CREATE TABLE TEST_TABLE(key varchar)") is Success(0)
      executor.runUpdate("INSERT INTO TEST_TABLE values ('one'), ('two'), ('three')") is Success(3)

      executor.runSelect[String, List]("SELECT * FROM TEST_TABLE")(_.getString("key")) is
        Success(List("one", "two", "three"))
    }
  }

  it should "return zero for count query when table is empty" in new DatabaseFixture {
    using(new SlickSQLExecutor(databaseConfig)) { executor =>
      executor.runUpdate("CREATE TABLE TEST_TABLE(key varchar)") is Success(0)
      executor.runCount("SELECT count(*) as count FROM TEST_TABLE")(_.getInt("count")) is Success(0)
    }
  }

  it should "return row count when table is non-empty" in new DatabaseFixture {
    using(new SlickSQLExecutor(databaseConfig)) { executor =>
      executor.runUpdate("CREATE TABLE TEST_TABLE(key varchar)") is Success(0)
      executor.runUpdate("INSERT INTO TEST_TABLE values ('one'), ('two'), ('three')") is Success(3)
      executor.runCount("SELECT count(*) as count FROM TEST_TABLE")(_.getInt("count")) is Success(3)
    }
  }

  it should "select from table with multiple rows" in new DatabaseFixture {
    using(new SlickSQLExecutor(databaseConfig)) { executor =>
      executor.runUpdate("""
          |CREATE TABLE TEST_TABLE(
          |   id SERIAL PRIMARY KEY,
          |   name VARCHAR NOT NULL,
          |   bool BOOLEAN NOT NUll
          |)
          |""".stripMargin) is Success(0)

      executor.runUpdate("""
          |INSERT INTO TEST_TABLE
          |values (0, 'name1', 'true'),
          |       (1, 'name2', 'false'),
          |       (2, 'name3', 'true')
          |""".stripMargin) is Success(3)

      case class Row(int: Int, string: String, bool: Boolean)

      val expectedResult =
        List(
          Row(int = 0, string = "name1", bool = true),
          Row(int = 1, string = "name2", bool = false),
          Row(int = 2, string = "name3", bool = true)
        )

      executor
        .runSelect[Row, List]("SELECT * FROM TEST_TABLE") { row =>
          Row(
            int    = row.getInt("id"),
            string = row.getString("name"),
            bool   = row.getBoolean("bool")
          )
        } is Success(expectedResult)

    }
  }
}
