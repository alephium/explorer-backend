// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.Address

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class ContractParent(
    parent: Option[Address] = None
)

object ContractParent {
  implicit val readWriter: ReadWriter[ContractParent] = macroRW
}
