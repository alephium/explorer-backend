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

package org.alephium.explorer.api

import com.typesafe.scalalogging.StrictLogging
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.Configuration
import sttp.tapir.generic.auto._
import sttp.tapir.server.vertx.streams.VertxStreams

import org.alephium.api._

trait BaseEndpoint extends ErrorExamples with TapirCodecs with TapirSchemasLike with StrictLogging {
  import Endpoints._
  import ApiError._

  implicit val customConfiguration: Configuration =
    Configuration.default.withDiscriminator("type")

  type BaseEndpoint[I, O] = Endpoint[Unit, I, ApiError[_ <: StatusCode], O, VertxStreams]

  val baseEndpoint: BaseEndpoint[Unit, Unit] =
    endpoint
      .errorOut(
        oneOf[ApiError[_ <: StatusCode]](
          error(BadRequest, { case BadRequest(_) => true }),
          error(InternalServerError, { case InternalServerError(_) => true }),
          error(NotFound, { case NotFound(_) => true }),
          error(ServiceUnavailable, { case ServiceUnavailable(_) => true }),
          error(Unauthorized, { case Unauthorized(_) => true })
        )
      )
}
