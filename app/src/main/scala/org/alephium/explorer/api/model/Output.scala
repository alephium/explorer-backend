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

import org.alephium.api.TapirSchemas._
import org.alephium.api.UtilJson._
import org.alephium.explorer.api.Json._
import org.alephium.explorer.api.Schemas.configuration
import org.alephium.json.Json._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, TransactionId}
import org.alephium.util.{TimeStamp, U256}
import org.alephium.util.AVector

sealed trait Output {
  def hint: Int
  def key: Hash
  def attoAlphAmount: U256
  def address: Address
  def tokens: Option[ArraySeq[Token]]
  def spent: Option[TransactionId]
  def fixedOutput: Boolean
}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
@upickle.implicits.key("AssetOutput")
final case class AssetOutput(
    hint: Int,
    key: Hash,
    attoAlphAmount: U256,
    address: Address,
    tokens: Option[ArraySeq[Token]] = None,
    lockTime: Option[TimeStamp] = None,
    message: Option[ByteString] = None,
    spent: Option[TransactionId] = None,
    fixedOutput: Boolean
) extends Output {}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
@upickle.implicits.key("ContractOutput")
final case class ContractOutput(
    hint: Int,
    key: Hash,
    attoAlphAmount: U256,
    address: Address,
    tokens: Option[ArraySeq[Token]] = None,
    spent: Option[TransactionId] = None,
    fixedOutput: Boolean
) extends Output

object Output {

  def toFixedAssetOutput(
      output: Output
  ): Option[org.alephium.api.model.FixedAssetOutput] = {
    output match {
      case asset: AssetOutput if asset.fixedOutput =>
        asset.address match {
          case assetAddress: Address.Asset =>
            val amount = org.alephium.api.model.Amount(asset.attoAlphAmount)
            Some(
              org.alephium.api.model.FixedAssetOutput(
                asset.hint,
                asset.key,
                amount,
                assetAddress,
                tokens = asset.tokens
                  .map(tokens => AVector.from(tokens.map(_.toProtocol())))
                  .getOrElse(AVector.empty),
                lockTime = asset.lockTime.getOrElse(TimeStamp.zero),
                asset.message.getOrElse(ByteString.empty)
              )
            )
          case _ => None
        }
      case _ => None
    }
  }

  def toProtocol(output: Output): Option[org.alephium.api.model.Output] =
    (output, output.address) match {
      case (asset: AssetOutput, assetAddress: Address.Asset) =>
        Some(
          org.alephium.api.model.AssetOutput(
            output.hint,
            output.key,
            org.alephium.api.model.Amount(output.attoAlphAmount),
            assetAddress,
            tokens =
              output.tokens.map(t => AVector.from(t.map(_.toProtocol()))).getOrElse(AVector.empty),
            lockTime = asset.lockTime.getOrElse(TimeStamp.zero),
            message = asset.message.getOrElse(ByteString.empty)
          )
        )
      case (_: ContractOutput, contractAddress: Address.Contract) =>
        Some(
          org.alephium.api.model.ContractOutput(
            output.hint,
            output.key,
            org.alephium.api.model.Amount(output.attoAlphAmount),
            contractAddress,
            tokens = output.tokens
              .map(tokens => AVector.from(tokens.map(_.toProtocol())))
              .getOrElse(AVector.empty)
          )
        )
      case _ => None
    }

  implicit val assetReadWriter: ReadWriter[AssetOutput]       = macroRW
  implicit val contractReadWriter: ReadWriter[ContractOutput] = macroRW

  implicit val outputReadWriter: ReadWriter[Output] =
    ReadWriter.merge(assetReadWriter, contractReadWriter)
  implicit val contractSchema: Schema[ContractOutput] = Schema.derived[ContractOutput]
  implicit val AssetSchema: Schema[AssetOutput]       = Schema.derived[AssetOutput]
  implicit val schema: Schema[Output]                 = Schema.derived[Output]
}
