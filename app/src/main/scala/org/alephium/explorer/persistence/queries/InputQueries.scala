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

package org.alephium.explorer.persistence.queries

import slick.dbio.DBIOAction
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}

import org.alephium.explorer.persistence.DBActionW
import org.alephium.explorer.persistence.model.InputEntity
import org.alephium.explorer.persistence.schema.CustomSetParameter._

object InputQueries {

  /** Inserts inputs or ignore rows with primary key conflict */
  def insertInputs(inputs: Iterable[InputEntity]): DBActionW[Int] =
    if (inputs.isEmpty) {
      DBIOAction.successful(0)
    } else {
      val in1 = inputs.take(inputs.size / 2)
      val in2 = inputs.drop(inputs.size / 2)

      val placeholder1 = paramPlaceholder(rows = in1.size, columns = 8)
      val placeholder2 = paramPlaceholder(rows = in2.size, columns = 8)

      val query1 =
        s"""
           |INSERT INTO inputs ("block_hash",
           |                    "tx_hash",
           |                    "timestamp",
           |                    "hint",
           |                    "output_ref_key",
           |                    "unlock_script",
           |                    "main_chain",
           |                    "order")
           |VALUES $placeholder1
           |ON CONFLICT
           |    ON CONSTRAINT inputs_pk
           |    DO NOTHING
           |""".stripMargin

      val query2 =
        s"""
           |INSERT INTO inputs ("block_hash",
           |                    "tx_hash",
           |                    "timestamp",
           |                    "hint",
           |                    "output_ref_key",
           |                    "unlock_script",
           |                    "main_chain",
           |                    "order")
           |VALUES $placeholder2
           |ON CONFLICT
           |    ON CONSTRAINT inputs_pk
           |    DO NOTHING
           |""".stripMargin

      val parameters1: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          in1 foreach { input =>
            params >> input.blockHash
            params >> input.txHash
            params >> input.timestamp
            params >> input.hint
            params >> input.outputRefKey
            params >> input.unlockScript
            params >> input.mainChain
            params >> input.order
        }
      val parameters2: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          in2 foreach { input =>
            params >> input.blockHash
            params >> input.txHash
            params >> input.timestamp
            params >> input.hint
            params >> input.outputRefKey
            params >> input.unlockScript
            params >> input.mainChain
            params >> input.order
        }

      SQLActionBuilder(
        queryParts = query1,
        unitPConv  = parameters1
      ).asUpdate.andThen(
        SQLActionBuilder(
          queryParts = query2,
          unitPConv  = parameters2
        ).asUpdate
      )
    }
}
