package org.alephium.explorer

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import org.alephium.crypto.ED25519PublicKey
import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.explorer.protocol.model._
import org.alephium.protocol.script.{PayTo, PubScript}
import org.alephium.util.{AVector, Duration, TimeStamp}

trait Generators {

  val timestampGen: Gen[TimeStamp]            = Gen.posNum[Long].map(TimeStamp.unsafe)
  val hashGen: Gen[Hash]                      = Gen.uuid.map(uuid => Hash.hash(uuid.toString))
  val blockEntryHashGen: Gen[BlockEntry.Hash] = hashGen.map(new BlockEntry.Hash(_))
  val groupIndexGen: Gen[GroupIndex]          = Gen.posNum[Int].map(GroupIndex.unsafe(_))
  val heightGen: Gen[Height]                  = Gen.posNum[Int].map(Height.unsafe(_))
  val publicKeyGen: Gen[ED25519PublicKey]     = Gen.oneOf(Seq(ED25519PublicKey.generate))
  val pubScriptGen: Gen[PubScript]            = publicKeyGen.map(PubScript.build(PayTo.PKH, _))

  val inputProtocolGen: Gen[InputProtocol] = for {
    shortKey    <- arbitrary[Int]
    txHash      <- hashGen
    outputIndex <- arbitrary[Int]
  } yield InputProtocol(shortKey, txHash, outputIndex)

  val inputGen: Gen[Input] = inputProtocolGen.map(_.toApi)

  val outputProtocolGen: Gen[OutputProtocol] = for {
    value     <- arbitrary[Long]
    pubScript <- pubScriptGen
  } yield OutputProtocol(pubScript, value)

  val outputGen: Gen[Output] = outputProtocolGen.map(_.toApi)

  val transactionProtocolGen: Gen[TransactionProtocol] = for {
    id         <- hashGen
    inputSize  <- Gen.choose(0, 10)
    inputs     <- Gen.listOfN(inputSize, inputProtocolGen)
    outputSize <- Gen.choose(2, 10)
    outputs    <- Gen.listOfN(outputSize, outputProtocolGen)
  } yield TransactionProtocol(id, AVector.from(inputs), AVector.from(outputs))

  val transactionGen: Gen[Transaction] = transactionProtocolGen.map(_.toApi)

  val blockEntryProtocolGen: Gen[BlockEntryProtocol] = for {
    hash            <- blockEntryHashGen
    timestamp       <- timestampGen
    chainFrom       <- groupIndexGen
    chainTo         <- groupIndexGen
    height          <- heightGen
    deps            <- Gen.listOfN(5, blockEntryHashGen)
    transactionSize <- Gen.choose(1, 10)
    transactions    <- Gen.listOfN(transactionSize, transactionProtocolGen)
  } yield
    BlockEntryProtocol(hash,
                       timestamp,
                       chainFrom,
                       chainTo,
                       height,
                       AVector.from(deps),
                       AVector.from(transactions))

  val blockEntryGen: Gen[BlockEntry] = blockEntryProtocolGen.map(_.toApi)

  def chainGen(size: Int,
               startTimestamp: TimeStamp,
               chainFrom: GroupIndex,
               chainTo: GroupIndex): Gen[Seq[BlockEntryProtocol]] =
    Gen.listOfN(size, blockEntryProtocolGen).map { blocks =>
      blocks
        .foldLeft((Seq.empty[BlockEntryProtocol], Height.zero, startTimestamp)) {
          case ((acc, height, timestamp), block) =>
            val deps: AVector[BlockEntry.Hash] =
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
                   startTimestamp: TimeStamp): Gen[Seq[Seq[BlockEntryProtocol]]] = {

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
