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

import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import io.circe.{Codec, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._

import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.protocol.model.BlockEntryProtocol
import org.alephium.explorer.web.HttpClient
import org.alephium.rpc.CirceUtils._

trait BlockFlowClient {
  import BlockFlowClient._
  def getBlock(from: GroupIndex, hash: BlockEntry.Hash): Future[Either[String, BlockEntity]]

  def getChainInfo(from: GroupIndex, to: GroupIndex): Future[Either[String, ChainInfo]]

  def getHashesAtHeight(from: GroupIndex,
                        to: GroupIndex,
                        height: Height): Future[Either[String, HashesAtHeight]]

  def getBlocksAtHeight(from: GroupIndex, to: GroupIndex, height: Height)(
      implicit executionContext: ExecutionContext): Future[Either[Seq[String], Seq[BlockEntity]]] =
    getHashesAtHeight(from, to, height).flatMap {
      case Right(hashesAtHeight) =>
        Future
          .sequence(hashesAtHeight.headers.map(hash => getBlock(from, hash)))
          .map { blocksEither =>
            val (errors, blocks) = blocksEither.partitionMap(identity)
            if (errors.nonEmpty) {
              Left(errors)
            } else {
              Right(blocks)
            }
          }
      case Left(error) => Future.successful(Left(Seq(error)))
    }
}

object BlockFlowClient {
  def apply(httpClient: HttpClient, address: Uri)(
      implicit executionContext: ExecutionContext): BlockFlowClient =
    new Impl(httpClient, address)

  private class Impl(httpClient: HttpClient, address: Uri)(
      implicit executionContext: ExecutionContext)
      extends BlockFlowClient {

    private def rpcRequest[P <: JsonRpc: Encoder](uri: Uri, jsonRpc: P): HttpRequest =
      HttpRequest(
        HttpMethods.POST,
        uri = uri,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          s"""{"jsonrpc":"2.0","id": 0,"method":"${jsonRpc.method}","params":${jsonRpc.asJson}}""")
      )

    @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
    private def request[P <: JsonRpc: Encoder, R: Codec](
        params: P,
        uri: Uri = address): Future[Either[String, R]] = {
      httpClient
        .request[Result[R]](
          rpcRequest(uri, params)
        )
        .map(_.map(_.result))
    }

    //TODO Introduce monad transformer helper for more readability
    def getBlock(fromGroup: GroupIndex,
                 hash: BlockEntry.Hash): Future[Either[String, BlockEntity]] =
      getSelfClique().flatMap {
        case Left(error) => Future.successful(Left(error))
        case Right(selfClique) =>
          selfClique
            .index(fromGroup) match {
            case Left(error) => Future.successful(Left(error))
            case Right(index) =>
              selfClique.peers
                .lift(index)
                .flatMap(peer => peer.rpcPort.map(rpcPort => (peer.address, rpcPort))) match {
                case None =>
                  Future.successful(
                    Left(s"cannot find peer for group $fromGroup (peers: ${selfClique.peers})"))
                case Some((peerAddress, rpcPort)) =>
                  val uri = Uri(s"http://${peerAddress.getHostAddress}:${rpcPort}")
                  request[GetBlock, BlockEntryProtocol](GetBlock(hash), uri).map(_.map(_.toEntity))
              }
          }
      }

    def getChainInfo(from: GroupIndex, to: GroupIndex): Future[Either[String, ChainInfo]] = {
      request[GetChainInfo, ChainInfo](GetChainInfo(from, to))
    }

    def getHashesAtHeight(from: GroupIndex,
                          to: GroupIndex,
                          height: Height): Future[Either[String, HashesAtHeight]] =
      request[GetHashesAtHeight, HashesAtHeight](
        GetHashesAtHeight(from, to, height)
      )

    private def getSelfClique(): Future[Either[String, SelfClique]] =
      request[GetSelfClique.type, SelfClique](
        GetSelfClique
      )
  }

  final case class Result[A: Codec](result: A)
  object Result {
    implicit def codec[A: Codec]: Codec[Result[A]] = deriveCodec[Result[A]]
  }

  final case class HashesAtHeight(headers: Seq[BlockEntry.Hash])
  object HashesAtHeight {
    implicit val codec: Codec[HashesAtHeight] = deriveCodec[HashesAtHeight]
  }

  final case class ChainInfo(currentHeight: Height)
  object ChainInfo {
    implicit val codec: Codec[ChainInfo] = deriveCodec[ChainInfo]
  }

  sealed trait JsonRpc {
    def method: String
  }

  final case class GetHashesAtHeight(fromGroup: GroupIndex, toGroup: GroupIndex, height: Height)
      extends JsonRpc {
    val method: String = "get_hashes_at_height"
  }
  object GetHashesAtHeight {
    implicit val codec: Codec[GetHashesAtHeight] = deriveCodec[GetHashesAtHeight]
  }

  final case class GetChainInfo(fromGroup: GroupIndex, toGroup: GroupIndex) extends JsonRpc {
    val method: String = "get_chain_info"
  }
  object GetChainInfo {
    implicit val codec: Codec[GetChainInfo] = deriveCodec[GetChainInfo]
  }

  final case class GetBlock(hash: BlockEntry.Hash) extends JsonRpc {
    val method: String = "get_block"
  }
  object GetBlock {
    implicit val codec: Codec[GetBlock] = deriveCodec[GetBlock]
  }

  final case object GetSelfClique extends JsonRpc {
    val method: String = "self_clique"
    implicit val encoder: Encoder[GetSelfClique.type] = new Encoder[GetSelfClique.type] {
      final def apply(selfClique: GetSelfClique.type): Json = JsonObject.empty.asJson
    }
  }

  final case class PeerAddress(address: InetAddress, rpcPort: Option[Int], wsPort: Option[Int])
  object PeerAddress {
    implicit val codec: Codec[PeerAddress] = deriveCodec[PeerAddress]
  }

  final case class SelfClique(peers: Seq[PeerAddress], groupNumPerBroker: Int) {
    def index(group: GroupIndex): Either[String, Int] =
      if (groupNumPerBroker <= 0) {
        Left(s"SelfClique.groupNumPerBroker ($groupNumPerBroker) cannot be less or equal to zero")
      } else {
        Right(group.value / groupNumPerBroker)
      }
  }
  object SelfClique {
    implicit val codec: Codec[SelfClique] = deriveCodec[SelfClique]
  }
}
