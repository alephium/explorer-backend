package org.alephium.explorer.web

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import sttp.tapir.server.akkahttp.{AkkaHttpServerOptions, RichAkkaHttpEndpoint}

import org.alephium.explorer.api.AddressesEndpoints
import org.alephium.explorer.api.model.AddressInfo
import org.alephium.explorer.service.TransactionService

class AddressServer(transactionService: TransactionService)(
    implicit val serverOptions: AkkaHttpServerOptions,
    executionContext: ExecutionContext)
    extends Server
    with AddressesEndpoints {
  val route: Route =
    getTransactionsByAddress.toRoute(address =>
      transactionService.getTransactionsByAddress(address).map(Right.apply)) ~
      getAddressInfo.toRoute(address =>
        for {
          balance      <- transactionService.getBalance(address)
          transactions <- transactionService.getTransactionsByAddress(address)
        } yield Right(AddressInfo(balance, transactions)))
}
