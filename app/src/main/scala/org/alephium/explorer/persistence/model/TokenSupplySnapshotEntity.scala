// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.alephium.protocol.model.TokenId
import org.alephium.util.{TimeStamp, U256}

final case class TokenSupplySnapshotEntity(token: TokenId, timestamp: TimeStamp, total: U256)
