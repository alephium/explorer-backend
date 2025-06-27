// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future

import io.vertx.ext.web._
import org.postgresql.util.PSQLException
import sttp.tapir.server.interceptor.exception._
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.vertx.{VertxFutureServerInterpreter, VertxFutureServerOptions}
import sttp.tapir.statusCode

import org.alephium.api.{ApiError, DecodeFailureHandler}
import org.alephium.api.{alphJsonBody => jsonBody}
import org.alephium.explorer.Metrics

trait Server extends DecodeFailureHandler with VertxFutureServerInterpreter {

  val exceptionHandler = new ExceptionHandler[Future] {
    override def apply(
        ctx: ExceptionContext
    )(implicit monad: sttp.monad.MonadError[Future]): Future[Option[ValuedEndpointOutput[_]]] = {
      ctx.e match {
        case psql: PSQLException
            if psql.getMessage == "ERROR: canceling statement due to statement timeout" =>
          Future.successful(
            Some(
              ValuedEndpointOutput(
                statusCode.and(jsonBody[ApiError.GatewayTimeout]),
                (ApiError.GatewayTimeout.statusCode, ApiError.GatewayTimeout(s"Query timeout"))
              )
            )
          )
        case e =>
          Future.successful(
            Some(
              ValuedEndpointOutput(
                statusCode.and(jsonBody[ApiError.InternalServerError]),
                (
                  ApiError.InternalServerError.statusCode,
                  ApiError.InternalServerError(s"Internal Server Error: ${e.getMessage}")
                )
              )
            )
          )
      }
    }
  }

  override def vertxFutureServerOptions: VertxFutureServerOptions =
    VertxFutureServerOptions.customiseInterceptors
      .decodeFailureHandler(
        myDecodeFailureHandler
      )
      .exceptionHandler(exceptionHandler)
      .metricsInterceptor(Metrics.prometheus.metricsInterceptor())
      .options

  def routes: ArraySeq[Router => Route]
}
