package org.alephium.explorer

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import org.alephium.crypto.ED25519PublicKey
import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.protocol.model._
import org.alephium.util.{AVector, Base58, Duration, TimeStamp}

trait Generators {

  lazy val timestampGen: Gen[TimeStamp]              = Gen.posNum[Long].map(TimeStamp.unsafe)
  lazy val hashGen: Gen[Hash]                        = Gen.const(()).map(_ => Hash.generate)
  lazy val blockEntryHashGen: Gen[BlockEntry.Hash]   = hashGen.map(new BlockEntry.Hash(_))
  lazy val transactionHashGen: Gen[Transaction.Hash] = hashGen.map(new Transaction.Hash(_))
  lazy val groupIndexGen: Gen[GroupIndex]            = Gen.posNum[Int].map(GroupIndex.unsafe(_))
  lazy val heightGen: Gen[Height]                    = Gen.posNum[Int].map(Height.unsafe(_))
  lazy val publicKeyGen: Gen[ED25519PublicKey]       = Gen.oneOf(Seq(ED25519PublicKey.generate))
  lazy val addressGen: Gen[Address]                  = hashGen.map(hash => Address.unsafe(Base58.encode(hash.bytes)))

  def chainIndexesGen(groupNum: Int): Seq[(GroupIndex, GroupIndex)] =
    for {
      i <- 0 to groupNum - 1
      j <- 0 to groupNum - 1
    } yield (GroupIndex.unsafe(i), GroupIndex.unsafe(j))

  lazy val outputRefProtocolGen: Gen[OutputProtocol.Ref] = for {
    scriptHint <- arbitrary[Int]
    key        <- hashGen
  } yield OutputProtocol.Ref(scriptHint, key)

  lazy val inputProtocolGen: Gen[InputProtocol] = for {
    outputRef    <- outputRefProtocolGen
    unlockScript <- Gen.identifier
  } yield InputProtocol(outputRef, unlockScript)

  lazy val outputProtocolGen: Gen[OutputProtocol] = for {
    amount        <- Gen.choose[Long](1, 10000000)
    createdHeight <- arbitrary[Int]
    address       <- addressGen
  } yield OutputProtocol(amount, createdHeight, address)

  lazy val transactionProtocolGen: Gen[TransactionProtocol] = for {
    hash       <- transactionHashGen
    inputSize  <- Gen.choose(0, 10)
    inputs     <- Gen.listOfN(inputSize, inputProtocolGen)
    outputSize <- Gen.choose(2, 10)
    outputs    <- Gen.listOfN(outputSize, outputProtocolGen)
  } yield TransactionProtocol(hash, AVector.from(inputs), AVector.from(outputs))

  lazy val blockEntryProtocolGen: Gen[BlockEntryProtocol] = for {
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

  def blockEntityGen(groupNum: Int,
                     chainFrom: GroupIndex,
                     chainTo: GroupIndex,
                     parent: Option[BlockEntity]): Gen[BlockEntity] =
    blockEntryProtocolGen.map { entry =>
      val deps   = parent.map(p => Seq.fill(2 * groupNum - 1)(p.hash)).getOrElse(Seq.empty)
      val height = Height.unsafe(parent.map(_.height.value + 1).getOrElse(0))
      entry
        .copy(chainFrom = chainFrom, chainTo = chainTo, height = height, deps = AVector.from(deps))
        .toEntity
    }

  def chainGen(size: Int,
               startTimestamp: TimeStamp,
               chainFrom: GroupIndex,
               chainTo: GroupIndex,
               groupNum: Int): Gen[Seq[BlockEntryProtocol]] =
    Gen.listOfN(size, blockEntryProtocolGen).map { blocks =>
      blocks
        .foldLeft((Seq.empty[BlockEntryProtocol], Height.zero, startTimestamp)) {
          case ((acc, height, timestamp), block) =>
            val deps: AVector[BlockEntry.Hash] =
              if (acc.isEmpty) AVector.empty else AVector.tabulate(groupNum)(_ => acc.last.hash)
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
    val indexes = chainIndexesGen(groupNum)
    Gen
      .listOfN(indexes.size, Gen.choose(1, maxChainSize))
      .map(_.zip(indexes).map {
        case (size, (from, to)) =>
          chainGen(size, startTimestamp, from, to, groupNum).sample.get
      })
  }

  def blockEntitiesToBlockEntries(blocks: Seq[Seq[BlockEntity]]): Seq[Seq[BlockEntry]] = {
    val outputs: Seq[OutputEntity] = blocks.toArray.toIndexedSeq
      .flatMap(_.toArray.toIndexedSeq.flatMap(_.outputs.toArray.toIndexedSeq))

    blocks.map(_.map { block =>
      val transactions =
        block.transactions.map { tx =>
          Transaction(
            tx.hash,
            block.timestamp,
            block.inputs
              .filter(_.txHash === tx.hash)
              .map(input => input.toApi(outputs.find(_.outputRefKey === input.key))),
            block.outputs.filter(_.txHash === tx.hash).map(_.toApi)
          )
        }
      BlockEntry(
        block.hash,
        block.timestamp,
        block.chainFrom,
        block.chainTo,
        block.height,
        block.deps,
        transactions,
        mainChain = true
      )
    })
  }
}
