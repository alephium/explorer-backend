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

import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

/** Common table indexes.
  */
object CommonIndex {

  /** Need for this multi-column index?
    *
    * Postgres uses this index for queries that order by `block_timestamp desc, tx_order asc` and
    * have large number of resulting rows.
    */
  def blockTimestampTxOrderIndex(table: Schema[_]): SqlAction[Int, NoStream, Effect] =
    sqlu"""
        create index if not exists #${table.name}_block_timestamp_tx_order_idx
                on #${table.name} (block_timestamp desc, tx_order asc);
        """

  def timestampIndex(table: Schema[_]): SqlAction[Int, NoStream, Effect] =
    sqlu"""
        create index if not exists #${table.name}_timestamp_idx
                on #${table.name} (block_timestamp desc);
        """

}
