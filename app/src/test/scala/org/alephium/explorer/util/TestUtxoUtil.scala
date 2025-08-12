// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.util

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.model.{Output, Transaction}
import org.alephium.explorer.util.UtxoUtil._
import org.alephium.protocol.config.GroupConfig

object TestUtxoUtil {

  def transactionContainsAddress(
      transaction: Transaction,
      address: ApiAddress
  )(implicit groupConfig: GroupConfig): Boolean = {
    transaction.outputs.exists(out => addressEqual(out.address, address)) || transaction.inputs
      .exists(
        _.address.map(inAddress => addressEqual(inAddress, address)).getOrElse(false)
      )
  }

  def transactionsByAddress(
      transactions: Seq[Transaction],
      address: ApiAddress
  )(implicit groupConfig: GroupConfig): Seq[Transaction] = {
    transactions.filter(transactionContainsAddress(_, address))
  }

  def outputsByAddress(
      transactions: Seq[Transaction],
      address: ApiAddress
  )(implicit groupConfig: GroupConfig): Seq[Output] = {
    transactions.flatMap(_.outputs).filter(out => addressEqual(out.address, address))
  }
}
