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

package org.alephium.explorer.persistence.queries.result

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import slick.jdbc.{GetResult, PositionedResult}

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model.InputEntityLike
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, AddressLike, TransactionId}
import org.alephium.util.U256

object InputsFromTxQR {

  val selectFields: String =
    """
      tx_hash,
      input_order,
      hint,
      output_ref_key,
      unlock_script,
      output_ref_tx_hash,
      output_ref_address,
      output_ref_groupless_address,
      output_ref_amount,
      output_ref_tokens,
      contract_input
    """

  implicit val inputsFromTxQRGetResult: GetResult[InputsFromTxQR] =
    (result: PositionedResult) =>
      InputsFromTxQR(
        txHash = result.<<,
        inputOrder = result.<<,
        hint = result.<<,
        outputRefKey = result.<<,
        unlockScript = result.<<?,
        outputRefTxHash = result.<<?,
        outputRefAddress = result.<<?,
        outputRefAddressLike = result.<<?,
        outputRefAmount = result.<<?,
        outputRefTokens = result.<<?,
        contractInput = result.<<
      )
}

/** Query result for [[org.alephium.explorer.persistence.queries.InputQueries.inputsFromTxs]] */
final case class InputsFromTxQR(
    txHash: TransactionId,
    inputOrder: Int,
    hint: Int,
    outputRefKey: Hash,
    unlockScript: Option[ByteString],
    outputRefTxHash: Option[TransactionId],
    outputRefAddress: Option[Address],
    outputRefAddressLike: Option[AddressLike],
    outputRefAmount: Option[U256],
    outputRefTokens: Option[ArraySeq[Token]],
    contractInput: Boolean
) extends InputEntityLike
