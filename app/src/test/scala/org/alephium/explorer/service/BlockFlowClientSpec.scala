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

import java.net.InetAddress

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future

import akka.testkit.SocketUtil
import io.vertx.core.Vertx
import io.vertx.ext.web._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import sttp.model.Uri
import sttp.tapir.server.vertx.VertxFutureServerInterpreter._

import org.alephium.api
import org.alephium.api.model
import org.alephium.explorer.AlephiumFutureSpec
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
import org.alephium.protocol.model.{CliqueId, GroupIndex, NetworkId}
import org.alephium.util.AVector

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.DefaultArguments"))
class BlockFlowClientSpec extends AlephiumFutureSpec with DatabaseFixtureForAll {

  val group                  = GroupIndex.Zero
  val localhost: InetAddress = InetAddress.getByName("127.0.0.1")

  "BlockFlowClient.fetchBlock" should {
    val port = SocketUtil.temporaryLocalPort(SocketUtil.Both)
    val _    = new BlockFlowClientSpec.BlockFlowServerMock(localhost, port)

    "fail if `directCliqueAccess = true` but other clique nodes aren't reachable" in {
      val blockFlowClient =
        BlockFlowClient(Uri(localhost.getHostAddress, port), groupSetting.groupNum, None, true)

      blockFlowClient
        .fetchBlock(group, blockHashGen.sample.get)
        .failed
        .futureValue is a[ExplorerError.UnreachableNode]
    }

    "succeed if `directCliqueAccess = false`" in {
      val blockFlowClient =
        BlockFlowClient(Uri(localhost.getHostAddress, port), groupSetting.groupNum, None, false)

      blockFlowClient.fetchBlock(group, blockHashGen.sample.get).futureValue is a[BlockEntity]
    }
  }
  "BlockFlowClient companion" should {
    def contractResult(value: model.Val): model.CallContractResult = {
      val result = callContractSucceededGen.sample.get
      result.copy(returns = value +: result.returns)
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
      forAll(tokenIdGen, multipleCallContractResult, valByteVecGen, valByteVecGen) {
        case (token, result, uri, address) =>
          val uriResult: model.CallContractResult     = contractResult(uri)
          val addressResult: model.CallContractResult = contractResult(address)

          val results: AVector[model.CallContractResult] =
            AVector(uriResult, addressResult) ++ result.results
          val callContract = result.copy(results = results)

          BlockFlowClient.extractNFTMetadata(token, callContract) is Some(
            NFTMetadata(
              token,
              uri.value.utf8String
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

object BlockFlowClientSpec extends ScalaFutures with IntegrationPatience {
  class BlockFlowServerMock(localhost: InetAddress, port: Int) extends api.Endpoints with Server {

    implicit val groupConfig: GroupConfig     = groupSetting.groupConfig
    val maybeApiKey: Option[api.model.ApiKey] = None

    val cliqueId = CliqueId.generate

    private val peer =
      model.PeerAddress(localhost, SocketUtil.temporaryLocalPort(SocketUtil.Both), 0, 0)

    private val vertx  = Vertx.vertx()
    private val router = Router.router(vertx)

    val routes: ArraySeq[Router => Route] =
      ArraySeq(
        route(getSelfClique.serverLogicSuccess(_ => { _: Unit =>
          Future.successful(
            model.SelfClique(cliqueId, AVector(peer), true, true)
          )
        })),
        route(getChainParams.serverLogicSuccess(_ => { _: Unit =>
          Future.successful(
            model.ChainParams(
              NetworkId.AlephiumDevNet,
              18,
              groupSetting.groupNum,
              groupSetting.groupNum
            )
          )
        })),
        route(getBlock.serverLogicSuccess(_ => { _ =>
          Future.successful(blockEntryProtocolGen.sample.get)
        }))
      )

    val server = vertx.createHttpServer().requestHandler(router)

    routes.foreach(route => route(router))

    logger.info(s"Full node listening on ${localhost.getHostAddress}:$port")
    server.listen(port, localhost.getHostAddress).asScala.futureValue
  }
}
