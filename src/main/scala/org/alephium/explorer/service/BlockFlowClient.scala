package org.alephium.explorer.service

import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import io.circe.{Codec, Encoder}
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Circe.hashCodec
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.explorer.web.HttpClient

trait BlockFlowClient {
  import BlockFlowClient._
  def getBlock(hash: Hash): Future[Either[String, BlockEntry]]

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

    private def request[P <: JsonRpc: Encoder, R: Codec](params: P): Future[Either[String, R]] =
      httpClient
        .request[Result[R]](
          rpcRequest(address, params)
        )
        .map(_.map(_.result))

    def getBlock(hash: Hash): Future[Either[String, BlockEntry]] =
      request[GetBlock, BlockEntry](GetBlock(hash))

    def getChainInfo(from: GroupIndex, to: GroupIndex): Future[Either[String, ChainInfo]] = {
      request[GetChainInfo, ChainInfo](GetChainInfo(from, to))
    }

    def getHashesAtHeight(from: GroupIndex,
                          to: GroupIndex,
                          height: Height): Future[Either[String, HashesAtHeight]] =
      request[GetHashesAtHeight, HashesAtHeight](
        GetHashesAtHeight(from, to, height)
      )
  }

  final case class Result[A: Codec](result: A)
  object Result {
    implicit def codec[A: Codec]: Codec[Result[A]] = deriveCodec[Result[A]]
  }

  final case class HashesAtHeight(headers: Seq[Hash])
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

  final case class GetBlock(hash: Hash) extends JsonRpc {
    val method: String = "get_block"
  }
  object GetBlock {
    implicit val codec: Codec[GetBlock] = deriveCodec[GetBlock]
  }
}
