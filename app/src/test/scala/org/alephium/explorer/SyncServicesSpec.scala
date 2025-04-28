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

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future
import scala.util._

import org.scalacheck.Gen
import org.scalamock.scalatest.MockFactory
import org.scalatest.TryValues._
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.config._
import org.alephium.explorer.error.ExplorerError._
import org.alephium.explorer.service.BlockFlowClient

/** Temporary placeholder. These tests should be merged into ApplicationSpec */
class SyncServicesSpec
    extends AlephiumFutureSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with MockFactory {

  "getBlockFlowPeers" should {
    val explorerConfig: ExplorerConfig = TestExplorerConfig()

    "return peer URIs" when {
      "directCliqueAccess = true" in {
        forAll(genSelfClique(Gen.nonEmptyListOf(genPeerAddress))) { selfClique =>
          implicit val client: BlockFlowClient = mock[BlockFlowClient]

          (client.fetchSelfClique _).expects() returns Future.successful(selfClique)

          val expectedPeers =
            SyncServices.urisFromPeers(selfClique.nodes)

          SyncServices
            .getBlockFlowPeers(
              directCliqueAccess = true,
              blockFlowUri = explorerConfig.blockFlowUri
            )
            .futureValue is expectedPeers
        }
      }

      "directCliqueAccess = false" in {
        implicit val client: BlockFlowClient = mock[BlockFlowClient]

        SyncServices
          .getBlockFlowPeers(directCliqueAccess = false, blockFlowUri = explorerConfig.blockFlowUri)
          .futureValue is ArraySeq(explorerConfig.blockFlowUri)
      }
    }

    "fail" when {
      "no peers" in {
        // Generate data with no peers
        forAll(genSelfClique(peers = Gen.const(List.empty))) { selfClique =>
          implicit val client: BlockFlowClient = mock[BlockFlowClient]

          // expect call to fetchSelfClique because directCliqueAccess = true
          (client.fetchSelfClique _).expects() returns Future.successful(selfClique)

          val result =
            SyncServices
              .getBlockFlowPeers(
                directCliqueAccess = true,
                blockFlowUri = explorerConfig.blockFlowUri
              )
              .failed
              .futureValue

          // expect PeersNotFound exception
          result is PeersNotFound(explorerConfig.blockFlowUri)
          // exception message should contain the Uri
          result.getMessage should include(explorerConfig.blockFlowUri.toString())
        }
      }
    }
  }

  "validateChainParams" should {
    "succeed" when {
      "networkId matches" in {
        val matchingNetworkId =
          for {
            networkId   <- genNetworkId
            chainParams <- genChainParams(networkId)
          } yield (networkId, chainParams) // generate matching networkId

        forAll(matchingNetworkId) { case (networkId, chainParams) =>
          SyncServices.validateChainParams(networkId, chainParams) is Success(())
        }
      }
    }

    "fail" when {
      "networkId is a mismatch" in {
        val mismatchedNetworkId =
          for {
            networkId   <- genNetworkId
            chainParams <- genChainParams(genNetworkId(exclude = networkId))
          } yield (networkId, chainParams)

        forAll(mismatchedNetworkId) { case (networkId, chainParams) =>
          SyncServices
            .validateChainParams(networkId, chainParams)
            .failure
            .exception is ChainIdMismatch(remote = chainParams.networkId, local = networkId)
        }
      }
    }
  }
}
