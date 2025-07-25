// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import scala.collection.immutable.ArraySeq

import com.typesafe.scalalogging.StrictLogging
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.EndpointIO.Example
import sttp.tapir.generic.Configuration
import sttp.tapir.generic.auto._
import sttp.tapir.server.vertx.streams.VertxStreams

import org.alephium.api._
import org.alephium.explorer.config.Default
import org.alephium.json.Json.ReadWriter
import org.alephium.protocol.config.GroupConfig

trait BaseEndpoint extends ErrorExamples with TapirCodecs with TapirSchemasLike with StrictLogging {
  import Endpoints._
  import ApiError._

  implicit val customConfiguration: Configuration =
    Schemas.configuration

  implicit val groupConfig: GroupConfig = Default.groupConfig

  type BaseEndpoint[I, O] = Endpoint[Unit, I, ApiError[_ <: StatusCode], O, VertxStreams]

  val baseEndpoint: BaseEndpoint[Unit, Unit] =
    endpoint
      .out(emptyOutput.description("Ok"))
      .errorOut(
        oneOf[ApiError[_ <: StatusCode]](
          error(BadRequest, { case BadRequest(_) => true }),
          error(InternalServerError, { case InternalServerError(_) => true }),
          error(NotFound, { case NotFound(_) => true }),
          error(ServiceUnavailable, { case ServiceUnavailable(_) => true }),
          error(Unauthorized, { case Unauthorized(_) => true }),
          error(GatewayTimeout, { case GatewayTimeout(_) => true })
        )
      )

  def arrayBody[T](tpe: String, maxSize: Int)(implicit
      examples: List[Example[ArraySeq[T]]],
      rw: ReadWriter[ArraySeq[T]],
      schema: Schema[ArraySeq[T]]
  ): EndpointIO.Body[String, ArraySeq[T]] =
    org.alephium.api.Endpoints
      .jsonBody[ArraySeq[T]]
      .validate(Validator.maxSize(maxSize))
      .description(s"List of $tpe, max items: $maxSize")
}
