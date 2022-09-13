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

import akka.util.ByteString
import slick.jdbc.{GetResult, PositionedResult}

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.util.{AVector, U256}

object InputsQR {
  implicit val inputsQRGetResult: GetResult[InputsQR] =
    (result: PositionedResult) =>
      InputsQR(
        hint             = result.<<,
        outputRefKey     = result.<<,
        unlockScript     = result.<<?,
        outputRefAddress = result.<<?,
        outputRefAmount  = result.<<?,
        outputRefTokens  = result.<<?
    )
}

/** Query result for [[org.alephium.explorer.persistence.queries.InputQueries.getInputsQuery]] */
final case class InputsQR(hint: Int,
                          outputRefKey: Hash,
                          unlockScript: Option[ByteString],
                          outputRefAddress: Option[Address],
                          outputRefAmount: Option[U256],
                          outputRefTokens: Option[AVector[Token]]) {

  def toApiInput(): Input =
    Input(outputRef      = OutputRef(hint, outputRefKey),
          unlockScript   = unlockScript,
          address        = outputRefAddress,
          attoAlphAmount = outputRefAmount,
          tokens         = outputRefTokens)
}
