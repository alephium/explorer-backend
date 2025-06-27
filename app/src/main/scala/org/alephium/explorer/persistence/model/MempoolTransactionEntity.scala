// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import scala.collection.immutable.ArraySeq

import org.alephium.explorer.api.model.{AssetOutput, ContractOutput, MempoolTransaction}
import org.alephium.explorer.util.AddressUtil
import org.alephium.protocol.model.{GroupIndex, TransactionId}
import org.alephium.util.{TimeStamp, U256}

final case class MempoolTransactionEntity(
    hash: TransactionId,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    gasAmount: Int,
    gasPrice: U256,
    lastSeen: TimeStamp
)

object MempoolTransactionEntity {
  def from(
      utx: MempoolTransaction
  ): (MempoolTransactionEntity, ArraySeq[UInputEntity], ArraySeq[UOutputEntity]) = {
    (
      MempoolTransactionEntity(
        utx.hash,
        utx.chainFrom,
        utx.chainTo,
        utx.gasAmount,
        utx.gasPrice,
        utx.lastSeen
      ),
      utx.inputs.zipWithIndex.map { case (input, order) =>
        UInputEntity(
          utx.hash,
          input.outputRef.hint,
          input.outputRef.key,
          input.unlockScript,
          input.address,
          input.address.flatMap(AddressUtil.convertToGrouplessAddress),
          order
        )
      },
      utx.outputs.zipWithIndex.map { case (output, order) =>
        val lockTime = output match {
          case asset: AssetOutput => asset.lockTime
          case _: ContractOutput  => None
        }
        val message = output match {
          case asset: AssetOutput => asset.message
          case _: ContractOutput  => None
        }
        UOutputEntity(
          utx.hash,
          output.hint,
          output.key,
          output.attoAlphAmount,
          output.address,
          AddressUtil.convertToGrouplessAddress(output.address),
          output.tokens,
          lockTime,
          message,
          order
        )
      }
    )
  }
}
