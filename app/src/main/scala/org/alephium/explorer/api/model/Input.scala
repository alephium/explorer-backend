// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

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
