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

import org.alephium.protocol.model.{Address, AddressLike, BlockHash, TransactionId}
import org.alephium.util.TimeStamp

final case class TransactionPerAddressEntity(
    address: Address,
    addressLike: Option[AddressLike],
    hash: TransactionId,
    blockHash: BlockHash,
    timestamp: TimeStamp,
    txOrder: Int,
    mainChain: Boolean,
    coinbase: Boolean
)
