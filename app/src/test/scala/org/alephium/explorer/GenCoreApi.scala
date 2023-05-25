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

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

import org.alephium.api.model._
import org.alephium.explorer.GenApiModel.bytesGen
import org.alephium.explorer.GenCommon.{genInetAddress, genPortNum}
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.Generators._
import org.alephium.protocol.model.{CliqueId, NetworkId, Target}
import org.alephium.util.{AVector, I256, U256}

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

  def valAddressGen: Gen[ValAddress] =
    addressAssetProtocolGen.map(ValAddress(_))

  @SuppressWarnings(
    Array("org.wartremover.warts.JavaSerializable",
          "org.wartremover.warts.Product",
          "org.wartremover.warts.Serializable")) // Wartremover is complaining, don't now why :/
  def valGen: Gen[Val] = {
    Gen.oneOf(
      valBoolGen,
      valI256Gen,
      valU256Gen,
      valByteVecGen,
      valAddressGen
    )
  }

  def transactionProtocolGen: Gen[Transaction] =
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

  def unsignedTxGen: Gen[UnsignedTx] =
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

  def transactionTemplateProtocolGen: Gen[TransactionTemplate] =
    for {
      unsigned         <- unsignedTxGen
      inputSignatures  <- Gen.listOfN(2, bytesGen)
      scriptSignatures <- Gen.listOfN(2, bytesGen)
    } yield
      TransactionTemplate(unsigned, AVector.from(inputSignatures), AVector.from(scriptSignatures))

}
