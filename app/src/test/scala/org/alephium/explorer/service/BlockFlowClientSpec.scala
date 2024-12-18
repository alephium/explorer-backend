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

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import akka.testkit.SocketUtil
import org.scalatest.Suite
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import sttp.model.Uri

import org.alephium.api
import org.alephium.api.model
import org.alephium.explorer._
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.api.model.{FungibleTokenMetadata, NFTCollectionMetadata, NFTMetadata}
import org.alephium.explorer.error.ExplorerError
import org.alephium.explorer.persistence.DatabaseFixtureForAll
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.web.Server
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.{CliqueId, ContractId, GroupIndex, NetworkId}
import org.alephium.util.AVector

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.DefaultArguments"))
class BlockFlowClientSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForAll
    with HttpServersFixture {

  val group = GroupIndex.Zero

  val blockflow = new BlockFlowClientSpec.BlockFlowServerMock()

  val servers: ArraySeq[TestHttpServer] = ArraySeq(blockflow)

  "BlockFlowClient.fetchBlock" should {

    "fail if `directCliqueAccess = true` but other clique nodes aren't reachable" in {
      val blockFlowClient =
        BlockFlowClient(
          Uri(blockflow.host.getHostAddress, blockflow.port),
          groupSetting.groupNum,
          None,
          true
        )

      blockFlowClient
        .fetchBlock(group, blockHashGen.sample.get)
        .failed
        .futureValue is a[ExplorerError.UnreachableNode]
    }

    "succeed if `directCliqueAccess = false`" in {
      val blockFlowClient =
        BlockFlowClient(
          Uri(blockflow.host.getHostAddress, blockflow.port),
          groupSetting.groupNum,
          None,
          false
        )

      blockFlowClient.fetchBlock(group, blockHashGen.sample.get).futureValue is a[BlockEntity]
    }
  }
  "BlockFlowClient companion" should {
    def contractResult(value: model.Val*): model.CallContractResult = {
      val result = callContractSucceededGen.sample.get
      result.copy(returns = AVector.from(value) ++ result.returns)
    }
    "extract fungible token metadata" in {
      forAll(
        tokenIdGen,
        multipleCallContractResult,
        valByteVecGen,
        valByteVecGen,
        valU256Gen
      ) { case (token, result, symbol, name, decimals) =>
        val symbolResult: model.CallContractResult   = contractResult(symbol)
        val nameResult: model.CallContractResult     = contractResult(name)
        val decimalsResult: model.CallContractResult = contractResult(decimals)

        val results: AVector[model.CallContractResult] =
          AVector(symbolResult, nameResult, decimalsResult) ++ result.results
        val callContract = result.copy(results = results)

        BlockFlowClient.extractFungibleTokenMetadata(token, callContract) is Some(
          FungibleTokenMetadata(
            token,
            symbol.value.utf8String,
            name.value.utf8String,
            decimals.value
          )
        )
      }
    }

    "extract nft metadata" in {
      forAll(tokenIdGen, multipleCallContractResult, valByteVecGen, valU256Gen, valByteVecGen) {

        case (token, result, contractId, nftIndex, uri) =>
          val uriResult: model.CallContractResult             = contractResult(uri)
          val contractIdIndexResult: model.CallContractResult = contractResult(contractId, nftIndex)

          val results: AVector[model.CallContractResult] =
            AVector(uriResult, contractIdIndexResult) ++ result.results
          val callContract = result.copy(results = results)

          BlockFlowClient.extractNFTMetadata(token, callContract) is Some(
            NFTMetadata(
              token,
              uri.value.utf8String,
              ContractId.from(contractId.value).get,
              nftIndex.value
            )
          )
      }
    }

    "extract nft collection metadata" in {
      forAll(addressContractProtocolGen, multipleCallContractResult, valByteVecGen) {
        case (contractAddress, result, uri) =>
          val uriResult: model.CallContractResult = contractResult(uri)

          val results: AVector[model.CallContractResult] =
            AVector(uriResult) ++ result.results
          val callContract = result.copy(results = results)

          BlockFlowClient.extractNFTCollectionMetadata(contractAddress, callContract) is Some(
            NFTCollectionMetadata(
              contractAddress,
              uri.value.utf8String
            )
          )
      }
    }
  }
}

object BlockFlowClientSpec {
  class BlockFlowServerMock()(implicit val executionContext: ExecutionContext)
      extends api.Endpoints
      with Suite
      with ScalaFutures
      with IntegrationPatience
      with TestHttpServer {

    implicit val groupConfig: GroupConfig           = groupSetting.groupConfig
    override val apiKeys: AVector[api.model.ApiKey] = AVector.empty
    val maybeApiKey: Option[api.model.ApiKey]       = None

    val cliqueId = CliqueId.generate

    private val peer =
      model.PeerAddress(host, SocketUtil.temporaryLocalPort(SocketUtil.Both), 0, 0)

    val server: Server = new Server {
      val endpointsLogic: ArraySeq[EndpointLogic] =
        ArraySeq(
          getSelfClique.serverLogicSuccess(_ => { (_: Unit) =>
            Future.successful(
              model.SelfClique(cliqueId, AVector(peer), true, true)
            )
          }),
          getChainParams.serverLogicSuccess(_ => { (_: Unit) =>
            Future.successful(
              model.ChainParams(
                NetworkId.AlephiumDevNet,
                18,
                groupSetting.groupNum,
                groupSetting.groupNum
              )
            )
          }),
          getBlock.serverLogicSuccess(_ => { _ =>
            Future.successful(blockEntryProtocolGen.sample.get)
          })
        )
    }
  }
}
