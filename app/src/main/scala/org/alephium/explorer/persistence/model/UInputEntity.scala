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

import akka.util.ByteString

import org.alephium.explorer.api.model.{Input, OutputRef}
import org.alephium.protocol.Hash
import org.alephium.protocol.model.Address
import org.alephium.protocol.model.TransactionId

final case class UInputEntity(
    txHash: TransactionId,
    hint: Int,
    outputRefKey: Hash,
    unlockScript: Option[ByteString],
    address: Option[Address],
    grouplessAddress: Option[GrouplessAddress],
    uinputOrder: Int
) {
  val toApi: Input = Input(
    OutputRef(hint, outputRefKey),
    unlockScript,
    None,
    address,
    None,
    None,
    contractInput = false
  )
}
