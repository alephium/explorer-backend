// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model.IntervalType
import org.alephium.util.TimeStamp

final case class HashrateEntity(
    timestamp: TimeStamp,
    value: BigDecimal,
    intervalType: IntervalType
)
