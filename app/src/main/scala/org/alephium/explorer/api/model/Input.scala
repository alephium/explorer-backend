// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import sttp.tapir.Schema

import org.alephium.api.{model => protocol}
import org.alephium.api.TapirSchemas._
import org.alephium.api.UtilJson._
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.{Address, TransactionId}
import org.alephium.util.U256

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class Input(
    outputRef: OutputRef,
    unlockScript: Option[ByteString] = None,
    txHashRef: Option[TransactionId] = None,
    address: Option[Address] = None,
    attoAlphAmount: Option[U256] = None,
    tokens: Option[ArraySeq[Token]] = None,
    contractInput: Boolean
) {
  def toProtocol(): protocol.AssetInput =
    protocol.AssetInput(
      outputRef = outputRef.toProtocol(),
      unlockScript = unlockScript.getOrElse(ByteString.empty)
    )
}

object Input {
  implicit val readWriter: ReadWriter[Input] = macroRW
  implicit val schema: Schema[Input]         = Schema.derived[Input]
}
