// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.explorer

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import org.alephium.api.{model => protocolApi}
import org.alephium.explorer.{BlockHash, Hash}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.protocol.{model => protocol}
import org.alephium.protocol.ALF
import org.alephium.protocol.config.GroupConfig
import org.alephium.util.{AVector, Base58, Duration, Number, TimeStamp, U256}

trait Generators {

  lazy val groupNum: Int                     = Gen.choose(2, 4).sample.get
  lazy val networkType: protocol.NetworkType = protocol.NetworkType.Devnet
  implicit lazy val groupConfig: GroupConfig = new GroupConfig { val groups = groupNum }

  lazy val timestampGen: Gen[TimeStamp]              = Gen.posNum[Long].map(TimeStamp.unsafe)
  lazy val hashGen: Gen[Hash]                        = Gen.const(()).map(_ => Hash.generate)
  lazy val blockHashGen: Gen[BlockHash]              = Gen.const(()).map(_ => BlockHash.generate)
  lazy val blockEntryHashGen: Gen[BlockEntry.Hash]   = blockHashGen.map(new BlockEntry.Hash(_))
  lazy val transactionHashGen: Gen[Transaction.Hash] = hashGen.map(new Transaction.Hash(_))
  lazy val groupIndexGen: Gen[GroupIndex]            = Gen.posNum[Int].map(GroupIndex.unsafe(_))
  lazy val heightGen: Gen[Height]                    = Gen.posNum[Int].map(Height.unsafe(_))
  lazy val addressGen: Gen[Address]                  = hashGen.map(hash => Address.unsafe(Base58.encode(hash.bytes)))

  lazy val addressProtocolGen: Gen[protocol.Address] =
    for {
      group <- Gen.choose(0, groupConfig.groups - 1)
    } yield {
      val groupIndex     = protocol.GroupIndex.unsafe(group)
      val (_, publicKey) = groupIndex.generateKey
      protocol.Address.p2pkh(networkType, publicKey)
    }

  lazy val amountGen: Gen[U256] = Gen.choose(1000L, Number.quadrillion).map(ALF.nanoAlf)

  lazy val chainIndexes: Seq[(GroupIndex, GroupIndex)] =
    for {
      i <- 0 to groupConfig.groups - 1
      j <- 0 to groupConfig.groups - 1
    } yield (GroupIndex.unsafe(i), GroupIndex.unsafe(j))

  lazy val outputRefProtocolGen: Gen[protocolApi.OutputRef] = for {
    scriptHint <- arbitrary[Int]
    key        <- hashGen
  } yield protocolApi.OutputRef(scriptHint, key)

  lazy val inputProtocolGen: Gen[protocolApi.Input] = for {
    outputRef    <- outputRefProtocolGen
    unlockScript <- Gen.option(hashGen.map(_.bytes))
  } yield protocolApi.Input(outputRef, unlockScript)

  def outputProtocolGen: Gen[protocolApi.Output] =
    for {
      amount  <- amountGen
      address <- addressProtocolGen
    } yield protocolApi.Output(amount, address, None)

  def transactionProtocolGen: Gen[protocolApi.Tx] =
    for {
      hash       <- transactionHashGen
      inputSize  <- Gen.choose(0, 10)
      inputs     <- Gen.listOfN(inputSize, inputProtocolGen)
      outputSize <- Gen.choose(2, 10)
      outputs    <- Gen.listOfN(outputSize, outputProtocolGen)
    } yield protocolApi.Tx(hash.value, AVector.from(inputs), AVector.from(outputs))

  def blockEntryProtocolGen: Gen[protocolApi.BlockEntry] =
    for {
      hash            <- blockEntryHashGen
      timestamp       <- timestampGen
      chainFrom       <- groupIndexGen
      chainTo         <- groupIndexGen
      height          <- heightGen
      deps            <- Gen.listOfN(5, blockEntryHashGen)
      transactionSize <- Gen.choose(1, 10)
      transactions    <- Gen.listOfN(transactionSize, transactionProtocolGen)
    } yield
      protocolApi.BlockEntry(hash.value,
                             timestamp,
                             chainFrom.value,
                             chainTo.value,
                             height.value,
                             AVector.from(deps.map(_.value)),
                             Some(AVector.from(transactions)))

  def blockEntityGen(groupNum: Int,
                     chainFrom: GroupIndex,
                     chainTo: GroupIndex,
                     parent: Option[BlockEntity]): Gen[BlockEntity] =
    blockEntryProtocolGen.map { entry =>
      val deps   = parent.map(p => Seq.fill(2 * groupNum - 1)(p.hash)).getOrElse(Seq.empty)
      val height = Height.unsafe(parent.map(_.height.value + 1).getOrElse(0))
      BlockFlowClient.blockProtocolToEntity(
        entry
          .copy(chainFrom = chainFrom.value,
                chainTo   = chainTo.value,
                height    = height.value,
                deps      = AVector.from(deps.map(_.value))))

    }

  def chainGen(size: Int,
               startTimestamp: TimeStamp,
               chainFrom: GroupIndex,
               chainTo: GroupIndex): Gen[Seq[protocolApi.BlockEntry]] =
    Gen.listOfN(size, blockEntryProtocolGen).map { blocks =>
      blocks
        .foldLeft((Seq.empty[protocolApi.BlockEntry], Height.genesis, startTimestamp)) {
          case ((acc, height, timestamp), block) =>
            val deps: Seq[BlockHash] =
              if (acc.isEmpty) Seq.empty else Seq.tabulate(groupConfig.groups)(_ => acc.last.hash)
            val newBlock = block.copy(height = height.value,
                                      deps      = AVector.from(deps),
                                      timestamp = timestamp,
                                      chainFrom = chainFrom.value,
                                      chainTo   = chainTo.value)
            (acc :+ newBlock, Height.unsafe(height.value + 1), timestamp + Duration.unsafe(1))
        } match { case (block, _, _) => block }
    }

  def blockFlowGen(maxChainSize: Int,
                   startTimestamp: TimeStamp): Gen[Seq[Seq[protocolApi.BlockEntry]]] = {
    val indexes = chainIndexes
    Gen
      .listOfN(indexes.size, Gen.choose(1, maxChainSize))
      .map(_.zip(indexes).map {
        case (size, (from, to)) =>
          chainGen(size, startTimestamp, from, to).sample.get
      })
  }

  def blockEntitiesToBlockEntries(blocks: Seq[Seq[BlockEntity]]): Seq[Seq[BlockEntry]] = {
    val outputs: Seq[OutputEntity] = blocks.flatMap(_.flatMap(_.outputs))

    blocks.map(_.map { block =>
      val transactions =
        block.transactions.map { tx =>
          Transaction(
            tx.hash,
            block.timestamp,
            block.inputs
              .filter(_.txHash === tx.hash)
              .map(input => input.toApi(outputs.find(_.key === input.outputRefKey))),
            block.outputs.filter(_.txHash === tx.hash).map(_.toApi(None))
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
