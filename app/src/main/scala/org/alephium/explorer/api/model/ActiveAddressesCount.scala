// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import sttp.tapir.Schema

sealed trait ActiveAddressesCount
object ActiveAddressesCount {
  final case class Json(data: Seq[TimedCount]) extends ActiveAddressesCount
  final case class Csv(data: String)           extends ActiveAddressesCount

  object Csv {
    val schema: Schema[String] =
      Schema.schemaForString
        .encodedExample("1767225600000,10879")
        .description("CSV with timestamp (unix ms) and count columns")
  }
}
