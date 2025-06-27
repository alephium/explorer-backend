// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

sealed trait ExportType

object ExportType {
  case object CSV  extends ExportType
  case object JSON extends ExportType
}
