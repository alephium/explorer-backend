package org.alephium.explorer

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import org.alephium.explorer.api.model.BlockEntry
import org.alephium.util.{AVector, Duration, TimeStamp}

trait Generators {

  val timestampGen: Gen[TimeStamp] = Gen.posNum[Long].map(TimeStamp.unsafe)

  val blockEntryGen: Gen[BlockEntry] = for {
    hash      <- Gen.identifier
    timestamp <- timestampGen
    chainFrom <- arbitrary[Int]
    chainTo   <- arbitrary[Int]
    height    <- arbitrary[Int]
    deps      <- Gen.listOf(Gen.alphaNumStr)
  } yield BlockEntry(hash, timestamp, chainFrom, chainTo, height, AVector.from(deps))

  def chainGen(size: Int,
               startTimestamp: TimeStamp,
               chainFrom: Int,
               chainTo: Int): Gen[Seq[BlockEntry]] =
    Gen.listOfN(size, blockEntryGen).map { blocks =>
      blocks
        .foldLeft((Seq.empty[BlockEntry], 0, startTimestamp)) {
          case ((acc, height, timestamp), block) =>
            val deps: AVector[String] = if (acc.isEmpty) AVector.empty else AVector(acc.last.hash)
            val newBlock = block.copy(height = height,
                                      deps      = deps,
                                      timestamp = timestamp,
                                      chainFrom = chainFrom,
                                      chainTo   = chainTo)
            (acc :+ newBlock, height + 1, timestamp + Duration.unsafe(1))
        } match { case (block, _, _) => block }
    }

  def blockFlowGen(groupNum: Int,
                   maxChainSize: Int,
                   startTimestamp: TimeStamp): Gen[Seq[Seq[BlockEntry]]] = {

    val chainIndexes: Seq[(Int, Int)] = for {
      i <- 0 to groupNum - 1
      j <- 0 to groupNum - 1
    } yield (i, j)

    Gen
      .listOfN(chainIndexes.size, Gen.choose(1, maxChainSize))
      .map(_.zip(chainIndexes).map {
        case (size, (from, to)) =>
          chainGen(size, startTimestamp, from, to).sample.get
      })
  }

}
