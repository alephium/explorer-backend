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

package org.alephium.explorer.persistence.queries

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.util.U256

object InfoQueries {
  def getAlphHoldersAction(pagination: Pagination): DBActionSR[(Address, U256)] =
    sql"""
      SELECT
        address,
        balance
      FROM
        holders
      ORDER BY
        balance DESC
    """
      .paginate(pagination)
      .asAS[(Address, U256)]

  def getTokenHoldersAction(token: TokenId, pagination: Pagination): DBActionSR[(Address, U256)] =
    sql"""
      SELECT
        address,
        balance
      FROM
        token_holders
      WHERE
        token = $token
      ORDER BY
        balance DESC
    """
      .paginate(pagination)
      .asAS[(Address, U256)]
}
