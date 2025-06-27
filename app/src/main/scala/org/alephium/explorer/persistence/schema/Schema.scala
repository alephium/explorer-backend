// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.AbstractTable

abstract class Schema[A](val name: String) {
  val table: TableQuery[_ <: AbstractTable[A]]
}
