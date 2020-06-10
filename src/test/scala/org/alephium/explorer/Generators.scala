package org.alephium.explorer

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex}
import org.alephium.util.{AVector, Duration, TimeStamp}

trait Generators {

  val timestampGen: Gen[TimeStamp]   = Gen.posNum[Long].map(TimeStamp.unsafe)
  val hashGen: Gen[Hash]             = Gen.uuid.map(uuid => Hash.hash(uuid.toString))
  val groupIndexGen: Gen[GroupIndex] = Gen.posNum[Int].map(GroupIndex.unsafe(_))

  val blockEntryGen: Gen[BlockEntry] = for {
    hash      <- hashGen
    timestamp <- timestampGen
    chainFrom <- groupIndexGen
    chainTo   <- groupIndexGen
    height    <- arbitrary[Int]
    deps      <- Gen.listOf(hashGen)
  } yield BlockEntry(hash, timestamp, chainFrom, chainTo, height, AVector.from(deps))

  def chainGen(size: Int,
               startTimestamp: TimeStamp,
               chainFrom: GroupIndex,
               chainTo: GroupIndex): Gen[Seq[BlockEntry]] =
    Gen.listOfN(size, blockEntryGen).map { blocks =>
      blocks
        .foldLeft((Seq.empty[BlockEntry], 0, startTimestamp)) {
          case ((acc, height, timestamp), block) =>
            val deps: AVector[Hash] =
              if (acc.isEmpty) AVector.empty else AVector(acc.last.hash)
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

    val chainIndexes: Seq[(GroupIndex, GroupIndex)] = for {
      i <- 0 to groupNum - 1
      j <- 0 to groupNum - 1
    } yield (GroupIndex.unsafe(i), GroupIndex.unsafe(j))

    Gen
      .listOfN(chainIndexes.size, Gen.choose(1, maxChainSize))
      .map(_.zip(chainIndexes).map {
        case (size, (from, to)) =>
          chainGen(size, startTimestamp, from, to).sample.get
      })
  }

}
