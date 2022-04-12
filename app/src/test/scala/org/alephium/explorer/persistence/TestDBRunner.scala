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

package org.alephium.explorer.persistence

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

object TestDBRunner {

  /**
    * Returns an instance of [[DBRunner_V2]] that should be closed by client after use.
    *
    * @param tables Prerequisite tables to drop and create for the test.
    * @param ec     Application level [[ExecutionContext]]
    * @return       A test [[DBRunner_V2]]
    */
  def apply(tables: TableQuery[_]*)(implicit ec: ExecutionContext): DBRunner_V2 = {
    implicit val runner = DBRunner_V2(DatabaseConfig.forConfig[PostgresProfile]("db"))
    dropCreateTables(tables: _*)
    runner
  }

  def dropCreateTables(tables: TableQuery[_]*)(implicit runner: DBRunner_V2,
                                               ec: ExecutionContext): Unit =
    if (tables.nonEmpty) {
      val dropAction   = DBInitializer_V2.dropTables(tables)
      val createAction = DBInitializer_V2.createTables(tables)
      val action       = dropAction andThen createAction
      val _            = Await.result(runner.run(action), 10.seconds)
    }

}
