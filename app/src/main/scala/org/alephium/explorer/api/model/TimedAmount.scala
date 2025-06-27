// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import sttp.tapir.{Schema, SchemaType}

import org.alephium.api.UtilJson._
import org.alephium.json.Json._
import org.alephium.util.TimeStamp

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class TimedAmount(
    timestamp: TimeStamp,
    amount: BigInteger
)

object TimedAmount {
  implicit val readWriter: ReadWriter[TimedAmount] = readwriter[(TimeStamp, BigInteger)].bimap(
    timedAmount => (timedAmount.timestamp, timedAmount.amount),
    { case (time, amount) => TimedAmount(time, amount) }
  )
  implicit val schema: Schema[TimedAmount] = Schema(
    schemaType =
      SchemaType.SArray[TimedAmount, Any](Schema.string)(t => ArraySeq(t.timestamp, t.amount))
  )
}
