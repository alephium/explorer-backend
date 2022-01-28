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

package org.alephium.explorer.persistence.schema

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.sql.SqlAction

trait Schema {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  def mainChainIndex(tableName: String): SqlAction[Int, NoStream, Effect] =
    if (config.profile == slick.jdbc.H2Profile) {
      //h2 doesn't support partial indexes
      sqlu"create index if not exists #${tableName}_main_chain_idx on #${tableName} (main_chain)"
    } else {
      sqlu"create index if not exists #${tableName}_main_chain_idx on #${tableName} (main_chain) where main_chain = true;"
    }
}
