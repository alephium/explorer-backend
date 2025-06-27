// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model.IntervalType
import org.alephium.protocol.model.GroupIndex
import org.alephium.util.TimeStamp

final case class TransactionHistoryEntity(
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    count: Long,
    intervalType: IntervalType
)
