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

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

import org.alephium.api.model._
import org.alephium.explorer.GenApiModel.bytesGen
import org.alephium.explorer.GenCommon.{genInetAddress, genPortNum}
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.model.Height
import org.alephium.protocol.model.{BlockHash, CliqueId, GroupIndex, NetworkId, Target}
import org.alephium.serde._
import org.alephium.util.{AVector, Duration, I256, TimeStamp, U256}

/** Generators for types supplied by Core `org.alephium.api` package */
@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
object GenCoreApi {

  val genPeerAddress: Gen[PeerAddress] =
    for {
      address      <- genInetAddress
      restPort     <- genPortNum
      wsPort       <- genPortNum
      minerApiPort <- genPortNum
    } yield
      PeerAddress(
        address      = address,
        restPort     = restPort,
        wsPort       = wsPort,
        minerApiPort = minerApiPort
      )

  def genSelfClique(peers: Gen[List[PeerAddress]] = Gen.listOf(genPeerAddress)): Gen[SelfClique] =
    for {
      peers     <- peers
      selfReady <- Arbitrary.arbitrary[Boolean]
      synced    <- Arbitrary.arbitrary[Boolean]
    } yield
      SelfClique(
        cliqueId  = CliqueId.generate,
        nodes     = AVector.from(peers),
        selfReady = selfReady,
        synced    = synced
      )

  def genChainParams(networkId: Gen[NetworkId] = genNetworkId): Gen[ChainParams] =
    for {
      networkId             <- networkId
      numZerosAtLeastInHash <- Gen.posNum[Int]
      groupNumPerBroker     <- Gen.posNum[Int]
      groups                <- Gen.posNum[Int]
    } yield
      ChainParams(
        networkId             = networkId,
        numZerosAtLeastInHash = numZerosAtLeastInHash,
        groupNumPerBroker     = groupNumPerBroker,
        groups                = groups
      )

  private val i256Gen: Gen[I256] =
    Gen.choose[BigInteger](I256.MinValue.v, I256.MaxValue.v).map(I256.unsafe)
  private val u256Gen: Gen[U256] =
    Gen.choose[BigInteger](U256.MinValue.v, U256.MaxValue.v).map(U256.unsafe)

  lazy val valBoolGen: Gen[ValBool]       = Arbitrary.arbitrary[Boolean].map(ValBool.apply)
  lazy val valI256Gen: Gen[ValI256]       = i256Gen.map(ValI256.apply)
  lazy val valU256Gen: Gen[ValU256]       = u256Gen.map(ValU256.apply)
  lazy val valByteVecGen: Gen[ValByteVec] = bytesGen.map(ValByteVec.apply)

  def valAddressGen()(implicit groupSetting: GroupSetting): Gen[ValAddress] = {
    addressAssetProtocolGen().map(ValAddress(_))
  }

  @SuppressWarnings(
    Array("org.wartremover.warts.JavaSerializable",
          "org.wartremover.warts.Product",
          "org.wartremover.warts.Serializable")) // Wartremover is complaining, don't now why :/
  def valGen()(implicit groupSetting: GroupSetting): Gen[Val] = {
    Gen.oneOf(
      valBoolGen,
      valI256Gen,
      valU256Gen,
      valByteVecGen,
      valAddressGen()
    )
  }

  def transactionProtocolGen(implicit groupSetting: GroupSetting): Gen[Transaction] =
    for {
      unsigned             <- unsignedTxGen
      scriptExecutionOk    <- arbitrary[Boolean]
      contractInputsSize   <- Gen.choose(0, 5)
      contractInputs       <- Gen.listOfN(contractInputsSize, outputRefProtocolGen)
      generatedOutputsSize <- Gen.choose(0, 5)
      generatedOutputs     <- Gen.listOfN(generatedOutputsSize, outputProtocolGen)
      inputSignatures      <- Gen.listOfN(2, bytesGen)
      scriptSignatures     <- Gen.listOfN(2, bytesGen)
    } yield
      Transaction(unsigned,
                  scriptExecutionOk,
                  AVector.from(contractInputs),
                  AVector.from(generatedOutputs),
                  AVector.from(inputSignatures),
                  AVector.from(scriptSignatures))

  def blockEntryProtocolGen(implicit groupSetting: GroupSetting): Gen[BlockEntry] =
    for {
      hash            <- blockHashGen
      timestamp       <- timestampGen
      chainFrom       <- GenApiModel.groupIndexGen
      chainTo         <- GenApiModel.groupIndexGen
      height          <- GenApiModel.heightGen
      deps            <- Gen.listOfN(2 * groupSetting.groupNum - 1, blockHashGen)
      transactionSize <- Gen.choose(1, 10)
      transactions    <- Gen.listOfN(transactionSize, transactionProtocolGen)
      nonce           <- bytesGen
      version         <- Gen.posNum[Byte]
      depStateHash    <- hashGen
      txsHash         <- hashGen
    } yield {
      //From `alephium` repo
      val numZerosAtLeastInHash = 37
      val target = Target.unsafe(
        BigInteger.ONE.shiftLeft(256 - numZerosAtLeastInHash).subtract(BigInteger.ONE))

      BlockEntry(
        hash,
        timestamp,
        chainFrom.value,
        chainTo.value,
        height.value,
        AVector.from(deps),
        AVector.from(transactions),
        nonce,
        version,
        depStateHash,
        txsHash,
        target.bits
      )
    }

  def unsignedTxGen(implicit groupSetting: GroupSetting): Gen[UnsignedTx] =
    for {
      hash       <- transactionHashGen
      version    <- Gen.posNum[Byte]
      networkId  <- Gen.posNum[Byte]
      scriptOpt  <- Gen.option(scriptGen)
      inputSize  <- Gen.choose(0, 10)
      inputs     <- Gen.listOfN(inputSize, inputProtocolGen)
      outputSize <- Gen.choose(2, 10)
      outputs    <- Gen.listOfN(outputSize, fixedOutputAssetProtocolGen)
      gasAmount  <- Gen.posNum[Int]
      gasPrice   <- Gen.posNum[Long].map(U256.unsafe)
    } yield
      UnsignedTx(hash,
                 version,
                 networkId,
                 scriptOpt,
                 gasAmount,
                 gasPrice,
                 AVector.from(inputs),
                 AVector.from(outputs))

  def transactionTemplateProtocolGen(
      implicit groupSetting: GroupSetting): Gen[TransactionTemplate] =
    for {
      unsigned         <- unsignedTxGen
      inputSignatures  <- Gen.listOfN(2, bytesGen)
      scriptSignatures <- Gen.listOfN(2, bytesGen)
    } yield
      TransactionTemplate(unsigned, AVector.from(inputSignatures), AVector.from(scriptSignatures))

  val outputRefProtocolGen: Gen[OutputRef] = for {
    hint <- arbitrary[Int]
    key  <- hashGen
  } yield OutputRef(hint, key)

  val inputProtocolGen: Gen[AssetInput] = for {
    outputRef    <- outputRefProtocolGen
    unlockScript <- unlockScriptProtocolGen
  } yield AssetInput(outputRef, serialize(unlockScript))

  def fixedOutputAssetProtocolGen(implicit groupSetting: GroupSetting): Gen[FixedAssetOutput] =
    for {
      hint     <- Gen.posNum[Int]
      key      <- hashGen
      amount   <- amountGen
      lockTime <- timestampGen
      address  <- addressAssetProtocolGen()
    } yield
      FixedAssetOutput(hint,
                       key,
                       Amount(amount),
                       address,
                       AVector.empty,
                       lockTime,
                       ByteString.empty)

  def outputAssetProtocolGen(implicit groupSetting: GroupSetting): Gen[AssetOutput] =
    fixedOutputAssetProtocolGen.map(_.upCast())

  def outputContractProtocolGen: Gen[ContractOutput] =
    for {
      hint    <- Gen.posNum[Int]
      key     <- hashGen
      amount  <- amountGen
      address <- addressContractProtocolGen
    } yield ContractOutput(hint, key, Amount(amount), address, AVector.empty)

  def outputProtocolGen(implicit groupSetting: GroupSetting): Gen[Output] =
    Gen.oneOf(outputAssetProtocolGen: Gen[Output], outputContractProtocolGen: Gen[Output])

  def scriptGen: Gen[Script] = Gen.hexStr.map(Script.apply)

  def chainGen(size: Int, startTimestamp: TimeStamp, chainFrom: GroupIndex, chainTo: GroupIndex)(
      implicit groupSetting: GroupSetting): Gen[ArraySeq[BlockEntry]] =
    Gen.listOfN(size, blockEntryProtocolGen).map { blocks =>
      blocks
        .foldLeft((ArraySeq.empty[BlockEntry], Height.genesis, startTimestamp)) {
          case ((acc, height, timestamp), block) =>
            val deps: AVector[BlockHash] =
              if (acc.isEmpty) {
                AVector.empty
              } else {
                block.deps.replace(parentIndex(chainTo), acc.last.hash)
              }
            val newBlock = block.copy(height = height.value,
                                      deps      = deps,
                                      timestamp = timestamp,
                                      chainFrom = chainFrom.value,
                                      chainTo   = chainTo.value)
            (acc :+ newBlock, Height.unsafe(height.value + 1), timestamp + Duration.unsafe(1))
        } match { case (block, _, _) => block }
    }

  def blockFlowGen(maxChainSize: Int, startTimestamp: TimeStamp)(
      implicit groupSetting: GroupSetting): Gen[ArraySeq[ArraySeq[BlockEntry]]] = {
    val indexes = groupSetting.groupIndexes
    Gen
      .listOfN(indexes.size, Gen.choose(1, maxChainSize))
      .map { list =>
        ArraySeq.from(list.zip(indexes).map {
          case (size, (from, to)) =>
            chainGen(size, startTimestamp, from, to).sample.get
        })
      }
  }
}
