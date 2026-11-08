// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future
import scala.concurrent.duration._
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
import org.alephium.explorer.util.Scheduler
import org.alephium.explorer.util.TestUtils._

/** Temporary placeholder. These tests should be merged into ApplicationSpec */
class SyncServicesSpec
    extends AlephiumFutureSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with MockFactory {

  "getPeersWithRetry" should {
    val config = TestExplorerConfig()

    "retry connection failures until the node becomes available" in {
      implicit val client: BlockFlowClient = mock[BlockFlowClient]
      val params                           = genChainParams(config.networkId).sample.get
      inSequence {
        (client.fetchChainParams _).expects() returning Future.failed(
          NodeError(new java.net.ConnectException("offline"))
        )
        (client.fetchChainParams _).expects() returning Future.failed(
          NodeError(new java.net.SocketTimeoutException("timeout"))
        )
        (client.fetchChainParams _).expects() returning Future.successful(params)
      }
      using(Scheduler("retry-test")) { implicit scheduler =>
        SyncServices
          .getPeersWithRetry(config.networkId, false, config.blockFlowUri, Some(10.millis))
          .futureValue is ArraySeq(config.blockFlowUri)
      }
    }

    "retry a connection failure during clique discovery" in {
      implicit val client: BlockFlowClient = mock[BlockFlowClient]
      val params                           = genChainParams(config.networkId).sample.get
      val clique = genSelfClique(Gen.nonEmptyListOf(genPeerAddress)).sample.get
      inSequence {
        (client.fetchChainParams _).expects() returning Future.successful(params)
        (client.fetchSelfClique _).expects() returning Future.failed(
          NodeError(new java.net.ConnectException("offline"))
        )
        (client.fetchChainParams _).expects() returning Future.successful(params)
        (client.fetchSelfClique _).expects() returning Future.successful(clique)
      }
      using(Scheduler("retry-test")) { implicit scheduler =>
        SyncServices
          .getPeersWithRetry(config.networkId, true, config.blockFlowUri, Some(10.millis))
          .futureValue is SyncServices.urisFromPeers(clique.nodes)
      }
    }

    "fail immediately when retries are disabled" in {
      implicit val client: BlockFlowClient = mock[BlockFlowClient]
      val error                            = NodeError(new java.net.ConnectException("offline"))
      (client.fetchChainParams _).expects() returning Future.failed(error)
      using(Scheduler("retry-test")) { implicit scheduler =>
        SyncServices
          .getPeersWithRetry(config.networkId, false, config.blockFlowUri, None)
          .failed
          .futureValue is error
      }
    }

    "not retry an API error" in {
      implicit val client: BlockFlowClient = mock[BlockFlowClient]
      val error                            = NodeError(NodeApiError("invalid API key"))
      (client.fetchChainParams _).expects() returning Future.failed(error)
      using(Scheduler("retry-test")) { implicit scheduler =>
        SyncServices
          .getPeersWithRetry(config.networkId, false, config.blockFlowUri, Some(10.millis))
          .failed
          .futureValue is error
      }
    }

    "not retry an incompatible network" in {
      implicit val client: BlockFlowClient = mock[BlockFlowClient]
      val network                          = genNetworkId(exclude = config.networkId).sample.get
      val params                           = genChainParams(network).sample.get
      (client.fetchChainParams _).expects() returning Future.successful(params)
      using(Scheduler("retry-test")) { implicit scheduler =>
        SyncServices
          .getPeersWithRetry(config.networkId, false, config.blockFlowUri, Some(10.millis))
          .failed
          .futureValue is ChainIdMismatch(network, config.networkId)
      }
    }
  }

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
