// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import sttp.tapir.Schema
import sttp.tapir.generic.Configuration

import org.alephium.api.TapirSchemas
import org.alephium.protocol.model._

object Schemas {
  implicit val configuration: Configuration = Configuration.default.withDiscriminator("type")

  implicit val contractIdSchema: Schema[ContractId] = TapirSchemas.hashSchema.as[ContractId]
}
