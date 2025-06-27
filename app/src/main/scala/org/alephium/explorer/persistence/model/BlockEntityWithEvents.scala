// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import scala.collection.immutable.ArraySeq

final case class BlockEntityWithEvents(
    block: BlockEntity,
    events: ArraySeq[EventEntity]
)
