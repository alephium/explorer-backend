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

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.protocol.model.{Address, TransactionId}
import org.alephium.util.U256

object InputsFromTxQR {
  implicit val inputsFromTxQRGetResult: GetResult[InputsFromTxQR] =
    (result: PositionedResult) =>
      InputsFromTxQR(
        txHash       = result.<<,
        inputOrder   = result.<<,
        hint         = result.<<,
        outputRefKey = result.<<,
        unlockScript = result.<<?,
        txHashRef    = result.<<?,
        address      = result.<<?,
        amount       = result.<<?,
        token        = result.<<?
    )
}

/** Query result for [[org.alephium.explorer.persistence.queries.InputQueries.inputsFromTxsNoJoin]] */
final case class InputsFromTxQR(txHash: TransactionId,
                                inputOrder: Int,
                                hint: Int,
                                outputRefKey: Hash,
                                unlockScript: Option[ByteString],
                                txHashRef: Option[TransactionId],
                                address: Option[Address],
                                amount: Option[U256],
                                token: Option[ArraySeq[Token]]) {

  def toApiInput(): Input =
    Input(outputRef      = OutputRef(hint, outputRefKey),
          unlockScript   = unlockScript,
          txHashRef      = txHashRef,
          address        = address,
          attoAlphAmount = amount,
          tokens         = token)
}
