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

package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model.{GroupIndex, Transaction, UTransaction}
import org.alephium.util.U256

final case class UTransactionEntity(
    hash: Transaction.Hash,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    startGas: Int,
    gasPrice: U256
)

object UTransactionEntity {
  def from(utx: UTransaction): (UTransactionEntity, Seq[UInputEntity], Seq[UOutputEntity]) = {
    (UTransactionEntity(
       utx.hash,
       utx.chainFrom,
       utx.chainTo,
       utx.startGas,
       utx.gasPrice
     ),
     utx.inputs.map { input =>
       UInputEntity(
         utx.hash,
         input.outputRef.scriptHint,
         input.outputRef.key,
         input.unlockScript
       )
     }.toSeq,
     utx.outputs.map { output =>
       UOutputEntity(
         utx.hash,
         output.amount,
         output.address,
         output.lockTime
       )
     }.toSeq)
  }
}
