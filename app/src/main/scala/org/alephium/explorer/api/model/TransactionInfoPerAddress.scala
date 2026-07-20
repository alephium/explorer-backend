// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.Address

final case class TransactionInfoPerAddress(
    address: Address,
    transactionInfo: TransactionInfo
)

object TransactionInfoPerAddress {
  implicit val readWriter: ReadWriter[TransactionInfoPerAddress] = macroRW
}
