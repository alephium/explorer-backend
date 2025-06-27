// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
