package org.alephium.explorer.service

import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.api.model.BlockEntry
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.web.HttpClient
import org.alephium.util.TimeStamp

trait BlockFlowSyncService {
  def sync(): Future[Unit]
}

object BlockFlowSyncService {
  def apply(httpClient: HttpClient, address: Uri, blockDao: BlockDao)(
      implicit executionContext: ExecutionContext): BlockFlowSyncService =
    new Impl(httpClient, address, blockDao)

  final case class FetchResponse(blocks: Seq[BlockEntry])
  final case class Result[A: Codec](result: A)

  implicit val fetchResponsecodec: Codec[FetchResponse] = deriveCodec[FetchResponse]
  implicit def resultCodec[A: Codec]: Codec[Result[A]]  = deriveCodec[Result[A]]

  private def rpcRequest(uri: Uri, method: String, params: String): HttpRequest =
    HttpRequest(HttpMethods.POST,
                uri = uri,
                entity =
                  HttpEntity(ContentTypes.`application/json`,
                             s"""{"jsonrpc":"2.0","id": 0,"method":"$method","params": $params}"""))

  private class Impl(httpClient: HttpClient, address: Uri, blockDao: BlockDao)(
      implicit executionContext: ExecutionContext)
      extends BlockFlowSyncService {
    private val timeRangeMinutes: Long = 20

    private def getBlockflow(): Future[Either[String, Seq[BlockEntry]]] = {
      val now    = TimeStamp.now()
      val fromTs = now.plusMinutesUnsafe(-timeRangeMinutes)
      val toTs   = now
      httpClient
        .request[Result[FetchResponse]](
          rpcRequest(address,
                     "blockflow_fetch",
                     s"""{"fromTs":${fromTs.millis},"toTs":${toTs.millis}}"""))
        .map(_.map(res => res.result.blocks))
    }

    def sync(): Future[Unit] =
      getBlockflow().flatMap {
        case Right(blocks) => Future.sequence(blocks.map(blockDao.insert)).map(_ => ())
        case Left(_)       => Future.successful(())
      }
  }
}
