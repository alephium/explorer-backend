package org.alephium.explorer.service

import java.net.InetAddress

import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import io.circe.{Codec, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._

import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.explorer.protocol.model.BlockEntryProtocol
import org.alephium.explorer.web.HttpClient
import org.alephium.rpc.CirceUtils._

trait BlockFlowClient {
  import BlockFlowClient._
  def getBlock(from: GroupIndex, hash: BlockEntry.Hash): Future[Either[String, BlockEntry]]

  def getChainInfo(from: GroupIndex, to: GroupIndex): Future[Either[String, ChainInfo]]

  def getHashesAtHeight(from: GroupIndex,
                        to: GroupIndex,
                        height: Height): Future[Either[String, HashesAtHeight]]
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
    def getBlock(fromGroup: GroupIndex, hash: BlockEntry.Hash): Future[Either[String, BlockEntry]] =
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
                  request[GetBlock, BlockEntryProtocol](GetBlock(hash), uri).map(_.map(_.toApi))
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
