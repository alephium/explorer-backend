package org.alephium.explorer.web

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import sttp.tapir.server.akkahttp.{AkkaHttpServerOptions, RichAkkaHttpEndpoint}

import org.alephium.explorer.api.{AddressesEndpoints, ApiError, TransactionEndpoints}
import org.alephium.explorer.service.TransactionService

class TransactionServer(transactionService: TransactionService)(
    implicit val serverOptions: AkkaHttpServerOptions,
    executionContext: ExecutionContext)
    extends Server
    with TransactionEndpoints
    with AddressesEndpoints {
  val route: Route = getTransactionById.toRoute(
    hash =>
      transactionService
        .getTransaction(hash)
        .map(_.toRight(ApiError.NotFound(hash.value.toHexString)))) ~
    getTransactionsByAddress.toRoute(address =>
      transactionService.getTransactionsByAddress(address).map(Right.apply))
}
