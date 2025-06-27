// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries.result

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import slick.jdbc.{GetResult, PositionedResult}

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model.{GrouplessAddress, InputEntityLike}
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, TransactionId}
import org.alephium.util.U256

object InputsQR {

  val selectFields: String =
    """
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

  implicit val inputsQRGetResult: GetResult[InputsQR] =
    (result: PositionedResult) =>
      InputsQR(
        hint = result.<<,
        outputRefKey = result.<<,
        unlockScript = result.<<?,
        outputRefTxHash = result.<<?,
        outputRefAddress = result.<<?,
        outputRefGrouplessAddress = result.<<?,
        outputRefAmount = result.<<?,
        outputRefTokens = result.<<?,
        contractInput = result.<<
      )
}

/** Query result for [[org.alephium.explorer.persistence.queries.InputQueries.getInputsQuery]] */
final case class InputsQR(
    hint: Int,
    outputRefKey: Hash,
    unlockScript: Option[ByteString],
    outputRefTxHash: Option[TransactionId],
    outputRefAddress: Option[Address],
    outputRefGrouplessAddress: Option[GrouplessAddress],
    outputRefAmount: Option[U256],
    outputRefTokens: Option[ArraySeq[Token]],
    contractInput: Boolean
) extends InputEntityLike
