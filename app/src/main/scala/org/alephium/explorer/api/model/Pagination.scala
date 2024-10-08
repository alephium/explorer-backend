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

@scala.annotation.nowarn
final case class Pagination private (page: Int, limit: Int) {
  val offset: Int = (page - 1) * limit
}

object Pagination {

  @scala.annotation.nowarn
  final case class Reversible private (page: Int, limit: Int, reverse: Boolean) {
    val offset: Int = (page - 1) * limit
  }
  object Reversible {
    @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
    def unsafe(page: Int, limit: Int, reverse: Boolean = false): Reversible = {
      Reversible(page, limit, reverse)
    }
  }
  val defaultPage: Int        = 1
  val defaultLimit: Int       = 20
  val futureDefaultLimit: Int = 10
  val maxLimit: Int           = 100
  val futureMaxLimit: Int     = 20

  def default: Pagination = Pagination(defaultPage, defaultLimit)

  def unsafe(page: Int, limit: Int): Pagination = {
    Pagination(page, limit)
  }
}
