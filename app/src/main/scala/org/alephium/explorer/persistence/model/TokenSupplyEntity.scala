// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.alephium.util.{TimeStamp, U256}

final case class TokenSupplyEntity(
    timestamp: TimeStamp,
    total: U256,
    circulating: U256,
    reserved: U256,
    locked: U256
)
