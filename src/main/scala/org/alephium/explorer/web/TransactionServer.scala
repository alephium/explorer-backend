package org.alephium.explorer.web

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Route
import sttp.tapir.server.akkahttp.{AkkaHttpServerOptions, RichAkkaHttpEndpoint}

import org.alephium.explorer.api.{ApiError, TransactionEndpoints}
import org.alephium.explorer.service.TransactionService

class TransactionServer(transactionService: TransactionService)(
    implicit val serverOptions: AkkaHttpServerOptions,
    executionContext: ExecutionContext)
    extends Server
    with TransactionEndpoints {
  val route: Route = getTransactionById.toRoute(id =>
    transactionService.getTransaction(id).map(_.toRight(ApiError.NotFound(id.toHexString))))
}
