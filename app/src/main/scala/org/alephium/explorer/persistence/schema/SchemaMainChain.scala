// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

abstract class SchemaMainChain[A](name: String) extends Schema[A](name) {
  def createMainChainIndex(): SqlAction[Int, NoStream, Effect] =
    sqlu"create index if not exists #${name}_main_chain_idx on #${name} (main_chain) where main_chain = true;"
}
