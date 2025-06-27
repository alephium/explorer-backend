// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import sttp.tapir._

import org.alephium.api.alphPlainTextBody

trait MetricsEndpoints extends BaseEndpoint with QueryParams {

  val metrics: BaseEndpoint[Unit, String] =
    baseEndpoint
      .in("metrics")
      .out(alphPlainTextBody)
      .summary("Exports all prometheus metrics")
}
