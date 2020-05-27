package org.alephium.explorer

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import org.alephium.explorer.api.model.BlockEntry
import org.alephium.util.{AVector, TimeStamp}

trait Generators {

  val timestampGen: Gen[TimeStamp] = Gen.posNum[Long].map(TimeStamp.unsafe)

  val blockEntryGen: Gen[BlockEntry] = for {
    hash      <- Gen.alphaNumStr
    timestamp <- timestampGen
    chainFrom <- arbitrary[Int]
    chainTo   <- arbitrary[Int]
    height    <- arbitrary[Int]
    deps      <- Gen.listOf(Gen.alphaNumStr)
  } yield BlockEntry(hash, timestamp, chainFrom, chainTo, height, AVector.from(deps))
}
