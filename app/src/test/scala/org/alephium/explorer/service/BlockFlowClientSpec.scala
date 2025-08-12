// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.net.InetAddress

import scala.collection.immutable.ArraySeq

import akka.testkit.SocketUtil
import akka.util.ByteString
import sttp.model.Uri

import org.alephium.api.model
import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.TestBlockFlowServer
import org.alephium.explorer.api.model.{FungibleTokenMetadata, NFTCollectionMetadata, NFTMetadata}
import org.alephium.explorer.error.ExplorerError
import org.alephium.explorer.persistence.DatabaseFixtureForAll
import org.alephium.explorer.persistence.model._
import org.alephium.protocol.model.{ContractId, GroupIndex}
import org.alephium.util.AVector

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.DefaultArguments"))
class BlockFlowClientSpec extends AlephiumFutureSpec with DatabaseFixtureForAll {

  val group                  = GroupIndex.Zero
  val localhost: InetAddress = InetAddress.getByName("127.0.0.1")

  def bytesToString(bytes: ByteString): String = bytes.utf8String.replaceAll("\u0000", "")

  "BlockFlowClient.fetchBlock" should {
    val port  = SocketUtil.temporaryLocalPort(SocketUtil.Both)
    val block = blockEntryProtocolGen.sample.get
    val _     = new TestBlockFlowServer(localhost, port, blockflow = ArraySeq(ArraySeq(block)))

    "fail if `directCliqueAccess = true` but other clique nodes aren't reachable" in {
      val blockFlowClient =
        BlockFlowClient(
          Uri(localhost.getHostAddress, port),
          groupSetting.groupNum,
          None,
          true,
          consensus
        )

      blockFlowClient
        .fetchBlock(group, block.hash)
        .failed
        .futureValue is a[ExplorerError.NodeError]
    }

    "succeed if `directCliqueAccess = false`" in {
      val blockFlowClient =
        BlockFlowClient(
          Uri(localhost.getHostAddress, port),
          groupSetting.groupNum,
          None,
          false,
          consensus
        )

      blockFlowClient.fetchBlock(group, block.hash).futureValue is a[BlockEntity]
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
            bytesToString(symbol.value),
            bytesToString(name.value),
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
              bytesToString(uri.value),
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
              bytesToString(uri.value)
            )
          )
      }
    }
  }
}
