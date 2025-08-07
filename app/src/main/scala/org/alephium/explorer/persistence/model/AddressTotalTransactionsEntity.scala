// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.util.TimeStamp

final case class AddressTotalTransactionsEntity(
    address: ApiAddress,
    total: Int,
    lastUpdate: TimeStamp
)
