// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import sttp.tapir.Schema

import org.alephium.api.TapirSchemas._
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.Hash

final case class OutputRef(hint: Int, key: Hash) {

  def toProtocol(): org.alephium.api.model.OutputRef =
    org.alephium.api.model.OutputRef(hint, key)
}

object OutputRef {
  implicit val readWriter: ReadWriter[OutputRef] = macroRW
  implicit val schema: Schema[OutputRef]         = Schema.derived[OutputRef]
}
