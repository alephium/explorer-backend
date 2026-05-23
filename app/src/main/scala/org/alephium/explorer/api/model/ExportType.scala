// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

sealed trait ExportType

object ExportType {
  case object CSV  extends ExportType
  case object JSON extends ExportType

  def validate(exportType: String): Either[String, ExportType] = exportType match {
    case "csv"  => Right(CSV)
    case "json" => Right(JSON)
    case _      => Left(s"Invalid export type: $exportType. Supported types are 'csv' and 'json'.")
  }
}
