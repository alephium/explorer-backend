// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
        alph_holders
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
