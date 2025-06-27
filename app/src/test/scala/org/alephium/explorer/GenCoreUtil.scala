// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import org.scalacheck.Gen

import org.alephium.protocol.ALPH
import org.alephium.util.{Number, TimeStamp, U256}

/** Generators for types supplied by Core `org.alephium.util` package */
object GenCoreUtil {

  // java.sql.timestamp is out of range with Long.MaxValue
  val timestampMaxValue: TimeStamp = TimeStamp.unsafe(253370764800000L) // Jan 01 9999 00:00:00

  val u256Gen: Gen[U256] = Gen.posNum[Long].map(U256.unsafe)
  val timestampGen: Gen[TimeStamp] =
    Gen.choose[Long](0, timestampMaxValue.millis).map(TimeStamp.unsafe)
  val amountGen: Gen[U256] = Gen.choose(1000L, Number.quadrillion).map(ALPH.nanoAlph)
}
