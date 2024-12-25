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

import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.protocol.model.TransactionId
import org.alephium.util.U256

object InfoFromTxsQR {

  val selectFields: String =
    "hash, version, network_id, script_opt, gas_amount, gas_price, script_execution_ok, input_signatures, script_signatures"

  implicit val infoFromTxsQRGetResult: GetResult[InfoFromTxsQR] =
    (result: PositionedResult) =>
      InfoFromTxsQR(
        txHash = result.<<,
        version = result.<<,
        networkId = result.<<,
        scriptOpt = result.<<?,
        gasAmount = result.<<,
        gasPrice = result.<<,
        scriptExecutionOk = result.<<,
        inputSignatures = result.<<?,
        scriptSignatures = result.<<?
      )

  def empty(): InfoFromTxsQR =
    InfoFromTxsQR(
      TransactionId.zero,
      0,
      0,
      None,
      0,
      U256.Zero,
      true,
      None,
      None
    )
}

/** Query result for [[org.alephium.explorer.persistence.queries.TransactionQueries]] */
final case class InfoFromTxsQR(
    txHash: TransactionId,
    version: Byte,
    networkId: Byte,
    scriptOpt: Option[String],
    gasAmount: Int,
    gasPrice: U256,
    scriptExecutionOk: Boolean,
    inputSignatures: Option[ArraySeq[ByteString]],
    scriptSignatures: Option[ArraySeq[ByteString]]
) {

  def info(): (Int, U256, Boolean) =
    (gasAmount, gasPrice, scriptExecutionOk)
}
