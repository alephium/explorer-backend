// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import java.time.Instant

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import sttp.tapir.Schema

import org.alephium.api.TapirSchemas._
import org.alephium.api.UtilJson._
import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.Json._
import org.alephium.explorer.util.UtxoUtil
import org.alephium.json.Json._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{Address, BlockHash, TokenId, TransactionId}
import org.alephium.util.{TimeStamp, U256}
import org.alephium.util.AVector

final case class UnsignedTx(
    hash: TransactionId,
    version: Byte,
    networkId: Byte,
    scriptOpt: Option[String],
    gasAmount: Int,
    gasPrice: U256,
    inputs: ArraySeq[Input],
    outputs: ArraySeq[AssetOutput]
)

object UnsignedTx {
  implicit val unsignedTxRW: ReadWriter[UnsignedTx] = macroRW
  implicit val schema: Schema[UnsignedTx] = Schema.derived[UnsignedTx]
}
