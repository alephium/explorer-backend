// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import akka.util.ByteString

import org.alephium.explorer.api.model.{Input, OutputRef}
import org.alephium.protocol.Hash
import org.alephium.protocol.model.Address
import org.alephium.protocol.model.TransactionId

final case class UInputEntity(
    txHash: TransactionId,
    hint: Int,
    outputRefKey: Hash,
    unlockScript: Option[ByteString],
    address: Option[Address],
    grouplessAddress: Option[GrouplessAddress],
    uinputOrder: Int
) {
  val toApi: Input = Input(
    OutputRef(hint, outputRefKey),
    unlockScript,
    None,
    address,
    None,
    None,
    contractInput = false
  )
}
