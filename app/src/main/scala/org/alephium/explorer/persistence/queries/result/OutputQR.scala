// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries.result

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import slick.jdbc.{GetResult, PositionedResult}

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model.{GrouplessAddress, OutputEntity, OutputEntityLike}
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, TransactionId}
import org.alephium.util.{TimeStamp, U256}

object OutputQR {

  val selectFields: String =
    "output_type, hint, key, amount, address, groupless_address, tokens, lock_time, message, spent_finalized, fixed_output"

  implicit val outputsQRGetResult: GetResult[OutputQR] =
    (result: PositionedResult) =>
      OutputQR(
        outputType = result.<<,
        hint = result.<<,
        key = result.<<,
        amount = result.<<,
        address = result.<<,
        grouplessAddress = result.<<?,
        tokens = result.<<?,
        lockTime = result.<<?,
        message = result.<<?,
        spentFinalized = result.<<?,
        fixedOutput = result.<<
      )
}

/** Query result for [[org.alephium.explorer.persistence.queries.OutputQueries.getOutputsQuery]] */
final case class OutputQR(
    outputType: OutputEntity.OutputType,
    hint: Int,
    key: Hash,
    amount: U256,
    address: Address,
    grouplessAddress: Option[GrouplessAddress],
    tokens: Option[ArraySeq[Token]],
    lockTime: Option[TimeStamp],
    message: Option[ByteString],
    spentFinalized: Option[TransactionId],
    fixedOutput: Boolean
) extends OutputEntityLike
