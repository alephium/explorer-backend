// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._

final case class TransactionInfoPerAddress(
    address: ApiAddress,
    transactionInfo: TransactionInfo
)

object TransactionInfoPerAddress {
  implicit val readWriter: ReadWriter[TransactionInfoPerAddress] = macroRW
}
