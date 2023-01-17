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

package org.alephium.explorer.api.model

final case class Pagination private (page: Int, limit: Int, reverse: Boolean) {
  val offset: Int   = (page - 1) * limit
  val query: String = s"LIMIT $limit OFFSET $offset"
}

object Pagination {

  val defaultPage: Int  = 1
  val defaultLimit: Int = 20
  val maxLimit: Int     = 100
  val thousand: Int     = 1000

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def unsafe(page: Int, limit: Int, reverse: Boolean = false): Pagination = {
    Pagination(page, limit, reverse)
  }
}
