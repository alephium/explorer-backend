// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries.result

import slick.jdbc.GetResult

import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.protocol.model.Address

final case class LatestTxInfoByAddressQR(
    lookupAddress: String,
    address: Address,
    tx: TxByAddressQR
)

object LatestTxInfoByAddressQR {
  implicit val latestTxInfoByAddressGetResult: GetResult[LatestTxInfoByAddressQR] =
    result =>
      LatestTxInfoByAddressQR(
        result.<<,
        result.<<,
        TxByAddressQR.transactionByAddressQRGetResult(result)
      )
}
