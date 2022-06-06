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

import org.alephium.api.model.{ChainParams, PeerAddress, SelfClique}
import org.alephium.explorer.GenCommon.{genInetAddress, genPortNum}
import org.alephium.explorer.GenCoreProtocol.genNetworkId
import org.alephium.protocol.model.{CliqueId, NetworkId}
import org.alephium.util.AVector

/** Generators for types supplied by Core `org.alephium.api` package */
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

  val genSelfClique: Gen[SelfClique] =
    for {
      peers     <- Gen.listOf(genPeerAddress)
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
}
