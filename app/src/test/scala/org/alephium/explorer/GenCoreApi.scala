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

import org.scalacheck.{Arbitrary, Gen}

import org.alephium.api.model._
import org.alephium.explorer.GenApiModel.bytesGen
import org.alephium.explorer.GenCommon.{genInetAddress, genPortNum}
import org.alephium.explorer.GenCoreProtocol.genNetworkId
import org.alephium.explorer.Generators._
import org.alephium.protocol.model.{CliqueId, NetworkId}
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
    Gen.choose[java.math.BigInteger](I256.MinValue.v, I256.MaxValue.v).map(I256.unsafe)
  private val u256Gen: Gen[U256] =
    Gen.choose[java.math.BigInteger](U256.MinValue.v, U256.MaxValue.v).map(U256.unsafe)

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
}
