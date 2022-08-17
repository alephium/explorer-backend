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
