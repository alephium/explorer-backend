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

import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}

import org.alephium.explorer.persistence.DBActionW
import org.alephium.explorer.persistence.model.OutputEntity
import org.alephium.explorer.persistence.schema.CustomSetParameter._

object OutputQueries {

  /** Inserts outputs or ignore rows with primary key conflict */
  def insertOutputs(outputs: Iterable[OutputEntity]): DBActionW[Int] =
    QueryUtil.splitUpdates(rows = outputs, queryRowParams = 10) { (outputs, placeholder) =>
      val query =
        s"""
           |INSERT INTO outputs ("block_hash",
           |                     "tx_hash",
           |                     "timestamp",
           |                     "hint",
           |                     "key",
           |                     "amount",
           |                     "address",
           |                     "main_chain",
           |                     "lock_time",
           |                     "order")
           |VALUES $placeholder
           |ON CONFLICT
           |    ON CONSTRAINT outputs_pk
           |    DO NOTHING
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          outputs foreach { output =>
            params >> output.blockHash
            params >> output.txHash
            params >> output.timestamp
            params >> output.hint
            params >> output.key
            params >> output.amount
            params >> output.address
            params >> output.mainChain
            params >> output.lockTime
            params >> output.order
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asUpdate
    }
}
