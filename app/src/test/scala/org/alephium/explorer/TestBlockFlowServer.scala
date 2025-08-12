// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import java.net.InetAddress

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future

import akka.testkit.SocketUtil
import io.vertx.core.Vertx
import io.vertx.ext.web._
import org.scalacheck.Gen
import sttp.tapir.server.vertx.VertxFutureServerInterpreter._

import org.alephium.api
import org.alephium.api.model
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.AlephiumFutures
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.api.model._
import org.alephium.explorer.config.Default
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.web.Server
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model._
import org.alephium.util.{AVector, Hex}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class TestBlockFlowServer(
    host: InetAddress,
    port: Int,
    blockflow: ArraySeq[ArraySeq[model.BlockEntry]] = ArraySeq.empty,
    uncles: ArraySeq[model.BlockEntry] = ArraySeq.empty,
    networkId: NetworkId = NetworkId.AlephiumTestNet
) extends api.Endpoints
    with Server
    with AlephiumFutures {

  implicit val groupConfig: GroupConfig = Default.groupConfig

  override val apiKeys: AVector[api.model.ApiKey] = AVector.empty
  val maybeApiKey: Option[api.model.ApiKey]       = None

  val cliqueId = CliqueId.generate

  val blocks           = blockflow.flatten
  val blocksWithUncles = blockflow.flatten ++ uncles

  private val peer = model.PeerAddress(host, SocketUtil.temporaryLocalPort(SocketUtil.Both), 0, 0)

  def fetchHashesAtHeight(
      from: GroupIndex,
      to: GroupIndex,
      height: Height
  ): model.HashesAtHeight =
    model.HashesAtHeight(AVector.from(blocks.collect {
      case block
          if block.chainFrom === from.value && block.chainTo === to.value && block.height === height.value =>
        block.hash
    }))

  private val vertx  = Vertx.vertx()
  private val router = Router.router(vertx)

  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      route(getBlock.serverLogicSuccess(_ => { (hash: BlockHash) =>
        Future.successful(blocksWithUncles.find(_.hash === hash).get)
      })),
      route(getBlockAndEvents.serverLogicSuccess(_ => { (hash: BlockHash) =>
        Future.successful(
          model.BlockAndEvents(
            blocksWithUncles.find(_.hash === hash).get,
            AVector.from(Gen.listOfN(3, contractEventByBlockHash).sample.get)
          )
        )
      })),
      route(getBlocks.serverLogicSuccess(_ => { (timeInterval: TimeInterval) =>
        Future.successful(
          model.BlocksPerTimeStampRange(
            AVector.from(
              blockflow
                .map(
                  _.filter(b => b.timestamp >= timeInterval.from && b.timestamp <= timeInterval.to)
                )
                .map(AVector.from(_))
            )
          )
        )
      })),
      route(getBlocksAndEvents.serverLogicSuccess(_ => { (timeInterval: TimeInterval) =>
        Future.successful(
          model.BlocksAndEventsPerTimeStampRange(
            AVector.from(
              blockflow
                .map(
                  _.filter(b => b.timestamp >= timeInterval.from && b.timestamp <= timeInterval.to)
                )
                .map(blocks =>
                  AVector
                    .from(
                      blocks.map(block =>
                        model.BlockAndEvents(
                          block,
                          AVector.from(Gen.listOfN(3, contractEventByBlockHash).sample.get)
                        )
                      )
                    )
                )
            )
          )
        )
      })),
      route(getHashesAtHeight.serverLogicSuccess(_ => { case (chainIndex, height) =>
        Future.successful(
          fetchHashesAtHeight(
            chainIndex.from,
            chainIndex.to,
            Height.unsafe(height)
          )
        )
      })),
      route(getChainInfo.serverLogicSuccess(_ => { (chainIndex: ChainIndex) =>
        Future.successful(
          model.ChainInfo(
            blocks
              .collect {
                case block
                    if block.chainFrom == chainIndex.from.value && block.chainTo === chainIndex.to.value =>
                  block.height
              }
              .maxOption
              .getOrElse(Height.genesis.value)
          )
        )
      })),
      route(listMempoolTransactions.serverLogicSuccess(_ => { _ =>
        val txs  = Gen.listOfN(5, transactionTemplateProtocolGen).sample.get
        val from = groupIndexGen.sample.get.value
        val to   = groupIndexGen.sample.get.value
        Future.successful(
          AVector(model.MempoolTransactions(from, to, AVector.from(txs)))
        )
      })),
      route(getSelfClique.serverLogicSuccess(_ => { _ =>
        Future.successful(
          model.SelfClique(cliqueId, AVector(peer), true, true)
        )
      })),
      route(getChainParams.serverLogicSuccess(_ => { _ =>
        Future.successful(
          model.ChainParams(networkId, 18, groupSetting.groupNum, groupSetting.groupNum)
        )
      })),
      route(contractState.serverLogicSuccess(_ => { case address =>
        val interfaceId = Gen.option(stdInterfaceIdGen).sample.get
        val idBytes: Option[model.Val] = interfaceId.map(id =>
          model.ValByteVec(Hex.from(s"${BlockFlowClient.interfaceIdPrefix}${id.id}").get)
        )
        val immFields = idBytes.map(AVector(_)).getOrElse(AVector.empty)
        Future.successful(
          model.ContractState(
            address,
            statefulContractGen.sample.get,
            hashGen.sample.get,
            None,
            immFields,
            AVector.empty,
            assetStateGen.sample.get
          )
        )
      })),
      route(multiCallContract.serverLogicSuccess(_ => { _ =>
        val symbol                                      = valByteVecGen.sample.get
        val name                                        = valByteVecGen.sample.get
        val decimals                                    = valU256Gen.sample.get
        val totalSupply                                 = valU256Gen.sample.get
        val symbolResult: model.CallContractResult      = contractResult(symbol)
        val nameResult: model.CallContractResult        = contractResult(name)
        val decimalsResult: model.CallContractResult    = contractResult(decimals)
        val totalSupplyResult: model.CallContractResult = contractResult(totalSupply)

        val results: AVector[model.CallContractResult] =
          AVector(symbolResult, nameResult, decimalsResult, totalSupplyResult)
        Future.successful(
          model.MultipleCallContractResult(results)
        )
      }))
    )

  private def contractResult(value: model.Val): model.CallContractResult = {
    val result = callContractSucceededGen.sample.get
    result.copy(returns = value +: result.returns)
  }

  val server = vertx.createHttpServer().requestHandler(router)

  routes.foreach(route => route(router))

  logger.info(s"Full node listening on ${host.getHostAddress}:$port")
  server.listen(port, host.getHostAddress).asScala.futureValue
}
