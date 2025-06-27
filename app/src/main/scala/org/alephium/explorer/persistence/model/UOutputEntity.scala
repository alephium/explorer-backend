// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import scala.collection.immutable.ArraySeq

import akka.util.ByteString

import org.alephium.explorer.api.model.{AssetOutput, Token}
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, TransactionId}
import org.alephium.util.{TimeStamp, U256}

final case class UOutputEntity(
    txHash: TransactionId,
    hint: Int,
    key: Hash,
    amount: U256,
    address: Address,
    grouplessAddress: Option[GrouplessAddress],
    tokens: Option[ArraySeq[Token]],
    lockTime: Option[TimeStamp],
    message: Option[ByteString],
    uoutputOrder: Int
) {
  val toApi: AssetOutput =
    AssetOutput(
      hint,
      key,
      amount,
      address,
      tokens,
      lockTime,
      message,
      None,
      fixedOutput = true
    )
}
