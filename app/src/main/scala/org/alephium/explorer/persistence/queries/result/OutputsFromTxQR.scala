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
import org.alephium.explorer.persistence.model.{OutputEntity, OutputEntityLike}
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, TransactionId}
import org.alephium.util.{TimeStamp, U256}

object OutputsFromTxQR {

  val selectFields: String =
    "tx_hash, output_order, output_type, hint, key, amount, address, tokens, lock_time, message, spent_finalized, fixed_output"

  implicit val outputsFromTxQRGetResult: GetResult[OutputsFromTxQR] =
    (result: PositionedResult) =>
      OutputsFromTxQR(
        txHash = result.<<,
        outputOrder = result.<<,
        outputType = result.<<,
        hint = result.<<,
        key = result.<<,
        amount = result.<<,
        address = result.<<,
        tokens = result.<<?,
        lockTime = result.<<?,
        message = result.<<?,
        spentFinalized = result.<<?,
        fixedOutput = result.<<
      )
}

/** Query result for [[org.alephium.explorer.persistence.queries.OutputQueries.outputsFromTxs]] */
final case class OutputsFromTxQR(
    txHash: TransactionId,
    outputOrder: Int,
    outputType: OutputEntity.OutputType,
    hint: Int,
    key: Hash,
    amount: U256,
    address: Address,
    tokens: Option[ArraySeq[Token]],
    lockTime: Option[TimeStamp],
    message: Option[ByteString],
    spentFinalized: Option[TransactionId],
    fixedOutput: Boolean
) extends OutputEntityLike
