// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db

sealed trait DBConnectionPool
case object DBConnectionPool {

  /** Uses HikariCP for connection-pooling */
  case object HikariCP extends DBConnectionPool {
    override def toString: String = this.productPrefix
  }

  /** Disables connection-pooling */
  @SuppressWarnings(Array("org.wartremover.warts.PlatformDefault"))
  case object Disabled extends DBConnectionPool {
    override def toString: String = this.productPrefix.toLowerCase
  }
}
