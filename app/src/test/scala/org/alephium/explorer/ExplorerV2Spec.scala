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

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Success

import com.typesafe.config.ConfigException
import org.scalacheck.Arbitrary
import org.scalamock.scalatest.MockFactory
import org.scalatest.TryValues._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import slick.basic.DatabaseConfig

import org.alephium.explorer.AlephiumSpec._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.config.{ApplicationConfig, ExplorerConfig}
import org.alephium.explorer.error.ExplorerError.{ChainIdMismatch, ImpossibleToFetchNetworkType}
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.util.TestUtils._

/** Temporary placeholder. These tests should be merged into ApplicationSpec  */
class ExplorerV2Spec
    extends AnyWordSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with ScalaFutures
    with MockFactory {

  implicit val executionContext: ExecutionContext =
    ExecutionContext.global

  "initialiseDatabase" should {
    "successfully connect" when {
      "readOnly mode" in {
        val database =
          Explorer
            .initialiseDatabase(readOnly = true, "db", DatabaseFixture.config)
            .futureValue(Timeout(5.seconds))

        using(database)(_ is a[DatabaseConfig[_]])
      }

      "readWrite mode" in {
        val database =
          Explorer
            .initialiseDatabase(readOnly = false, "db", DatabaseFixture.config)
            .futureValue(Timeout(5.seconds))

        using(database)(_ is a[DatabaseConfig[_]])
      }
    }

    "fail connection" when {
      "database path is invalid" in {
        Seq(true, false) foreach { mode =>
          Explorer
            .initialiseDatabase(readOnly = mode, "invalid path", DatabaseFixture.config)
            .failed
            .futureValue is a[ConfigException.Missing]
        }
      }
    }
  }

  "getBlockFlowPeers" should {
    "return peer URIs" when {
      val explorerConfig: ExplorerConfig =
        ApplicationConfig.load().flatMap(ExplorerConfig(_)).success.value

      "directCliqueAccess = true" in {
        forAll(genSelfClique) { selfClique =>
          implicit val client: BlockFlowClient = mock[BlockFlowClient]

          (client.fetchSelfClique _).expects() returns Future.successful(Right(selfClique))

          val expectedPeers =
            Explorer.urisFromPeers(selfClique.nodes.toSeq)

          Explorer
            .getBlockFlowPeers(directCliqueAccess = true,
                               blockFlowUri       = explorerConfig.blockFlowUri)
            .futureValue is expectedPeers
        }
      }

      "directCliqueAccess = false" in {
        implicit val client: BlockFlowClient = mock[BlockFlowClient]

        Explorer
          .getBlockFlowPeers(directCliqueAccess = false, blockFlowUri = explorerConfig.blockFlowUri)
          .futureValue is Seq(explorerConfig.blockFlowUri)
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
          } yield (networkId, chainParams) //generate matching networkId

        forAll(matchingNetworkId) {
          case (networkId, chainParams) =>
            Explorer.validateChainParams(networkId, Right(chainParams)) is Success(())
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

        forAll(mismatchedNetworkId) {
          case (networkId, chainParams) =>
            Explorer
              .validateChainParams(networkId, Right(chainParams))
              .failure
              .exception is ChainIdMismatch(remote = chainParams.networkId, local = networkId)
        }
      }

      "response was an error" in {
        forAll(genNetworkId, Arbitrary.arbitrary[String]) {
          case (networkId, error) =>
            Explorer
              .validateChainParams(networkId, Left(error))
              .failure
              .exception is ImpossibleToFetchNetworkType(error)
        }
      }
    }
  }
}
