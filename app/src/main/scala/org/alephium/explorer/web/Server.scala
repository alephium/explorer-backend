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

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq

import scala.concurrent._
import sttp.tapir.server.interceptor.exception._
import org.alephium.api.ApiError
import io.vertx.ext.web._
import sttp.tapir.server.vertx.{VertxFutureServerInterpreter, VertxFutureServerOptions}
import sttp.tapir.server.model.ValuedEndpointOutput
import org.postgresql.util.PSQLException

import org.alephium.api.DecodeFailureHandler
import org.alephium.api.{alphJsonBody => jsonBody}
import org.alephium.explorer.Metrics
import org.alephium.explorer.config.ExplorerConfig

trait Server extends DecodeFailureHandler with VertxFutureServerInterpreter {

  def serverConfig: ExplorerConfig.Server

  private val statementTimeoutMessage = "ERROR: canceling statement due to statement timeout"

  val customExceptionHandler = new ExceptionHandler[Future] {
    override def apply(
        ctx: ExceptionContext
    )(implicit monad: sttp.monad.MonadError[Future]): Future[Option[ValuedEndpointOutput[_]]] = {
      ctx.e match {
        case psql: PSQLException if psql.getMessage == statementTimeoutMessage =>
          Future.successful(
            Some(
              ValuedEndpointOutput(
                jsonBody[ApiError.ServiceUnavailable],
                ApiError.ServiceUnavailable(
                  s"Query timeout. Please try again on: ${serverConfig.slowQueriesUri}"
                )
              )
            )
          )
        case _ => Future.successful(None) // If not handled, fallback to default behavior
      }
    }
  }

  override def vertxFutureServerOptions: VertxFutureServerOptions =
    VertxFutureServerOptions.customiseInterceptors
      .decodeFailureHandler(
        myDecodeFailureHandler
      )
      .exceptionHandler(customExceptionHandler)
      .metricsInterceptor(Metrics.prometheus.metricsInterceptor())
      .options

  def routes: ArraySeq[Router => Route]

}
