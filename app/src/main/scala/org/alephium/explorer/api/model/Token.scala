// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import sttp.tapir.Schema

import org.alephium.api.TapirSchemas._
import org.alephium.explorer.api.Json._
import org.alephium.explorer.api.Schemas._
import org.alephium.json.Json._
import org.alephium.protocol.model.TokenId
import org.alephium.serde._
import org.alephium.util.U256

final case class Token(id: TokenId, amount: U256) {
  def toProtocol(): org.alephium.api.model.Token = org.alephium.api.model.Token(id, amount)
}

object Token {
  implicit val readWriter: ReadWriter[Token] = macroRW
  implicit val serde: Serde[Token] =
    Serde.forProduct2(
      Token.apply,
      t => (t.id, t.amount)
    )
  implicit val schema: Schema[Token] = Schema.derived[Token]
}
