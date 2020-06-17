package org.alephium.explorer

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.util.{AVector, Duration, TimeStamp}

trait Generators {

  val timestampGen: Gen[TimeStamp]   = Gen.posNum[Long].map(TimeStamp.unsafe)
  val hashGen: Gen[Hash]             = Gen.uuid.map(uuid => Hash.hash(uuid.toString))
  val groupIndexGen: Gen[GroupIndex] = Gen.posNum[Int].map(GroupIndex.unsafe(_))
  val heightGen: Gen[Height]         = Gen.posNum[Int].map(Height.unsafe(_))

  val inputGen: Gen[Input] = for {
    shortKey    <- arbitrary[Int]
    txHash      <- hashGen
    outputIndex <- arbitrary[Int]
  } yield Input(shortKey, txHash, outputIndex)

  val outputGen: Gen[Output] = for {
    value     <- arbitrary[Long]
    pubScript <- Gen.alphaNumStr
  } yield Output(value, pubScript)

  val transactionGen: Gen[Transaction] = for {
    id         <- hashGen
    inputSize  <- Gen.choose(0, 10)
    inputs     <- Gen.listOfN(inputSize, inputGen)
    outputSize <- Gen.choose(2, 10)
    outputs    <- Gen.listOfN(outputSize, outputGen)
  } yield Transaction(id, AVector.from(inputs), AVector.from(outputs))

  val blockEntryGen: Gen[BlockEntry] = for {
    hash            <- hashGen
    timestamp       <- timestampGen
    chainFrom       <- groupIndexGen
    chainTo         <- groupIndexGen
    height          <- heightGen
    deps            <- Gen.listOfN(5, hashGen)
    transactionSize <- Gen.choose(1, 10)
    transactions    <- Gen.listOfN(transactionSize, transactionGen)
  } yield
    BlockEntry(hash,
               timestamp,
               chainFrom,
               chainTo,
               height,
               AVector.from(deps),
               AVector.from(transactions))

  def chainGen(size: Int,
               startTimestamp: TimeStamp,
               chainFrom: GroupIndex,
               chainTo: GroupIndex): Gen[Seq[BlockEntry]] =
    Gen.listOfN(size, blockEntryGen).map { blocks =>
      blocks
        .foldLeft((Seq.empty[BlockEntry], Height.zero, startTimestamp)) {
          case ((acc, height, timestamp), block) =>
            val deps: AVector[Hash] =
              if (acc.isEmpty) AVector.empty else AVector(acc.last.hash)
            val newBlock = block.copy(height = height,
                                      deps      = deps,
                                      timestamp = timestamp,
                                      chainFrom = chainFrom,
                                      chainTo   = chainTo)
            (acc :+ newBlock, Height.unsafe(height.value + 1), timestamp + Duration.unsafe(1))
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
