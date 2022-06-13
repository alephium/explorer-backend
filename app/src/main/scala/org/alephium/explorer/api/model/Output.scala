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

import akka.util.ByteString

import org.alephium.api.UtilJson._
import org.alephium.explorer.Hash
import org.alephium.explorer.api.Json.{hashReadWriter, u256ReadWriter}
import org.alephium.json.Json._
import org.alephium.util.{TimeStamp, U256}

sealed trait Output {
  def hint: Int
  def key: Hash
  def amount: U256
  def address: Address
  def tokens: Option[Seq[Token]]
  def spent: Option[Transaction.Hash]
}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
@upickle.implicits.key("AssetOutput")
final case class AssetOutput(
    hint: Int,
    key: Hash,
    amount: U256,
    address: Address,
    tokens: Option[Seq[Token]]         = None,
    lockTime: Option[TimeStamp]        = None,
    additionalData: Option[ByteString] = None,
    spent: Option[Transaction.Hash]    = None
) extends Output

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
@upickle.implicits.key("ContractOutput")
final case class ContractOutput(
    hint: Int,
    key: Hash,
    amount: U256,
    address: Address,
    tokens: Option[Seq[Token]]      = None,
    spent: Option[Transaction.Hash] = None
) extends Output

object Output {
  implicit val assetReadWriter: ReadWriter[AssetOutput]       = macroRW
  implicit val contractReadWriter: ReadWriter[ContractOutput] = macroRW

  implicit val outputReadWriter: ReadWriter[Output] =
    ReadWriter.merge(assetReadWriter, contractReadWriter)
}
