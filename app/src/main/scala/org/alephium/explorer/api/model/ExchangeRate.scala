// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.json.Json._

final case class ExchangeRate(currency: String, name: String, unit: String, value: Double)

object ExchangeRate {
  implicit val readWriter: ReadWriter[ExchangeRate] = macroRW
}
