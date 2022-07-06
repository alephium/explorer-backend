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
package org.alephium.explorer.util

import slick.dbio.Effect
import slick.jdbc.SQLActionBuilder
import slick.sql.SqlStreamingAction

object SlickTestUtil {

  implicit class SlickTestImplicits(sql: SQLActionBuilder) {

    /** Adds EXPLAIN ANALYZE to the head of the query */
    def explain(): SqlStreamingAction[Vector[String], String, Effect.Read] =
      sql
        .copy(queryParts = sql.queryParts.updated(0, s"EXPLAIN ANALYZE ${sql.queryParts.head}"))
        .as[String]
  }
}
