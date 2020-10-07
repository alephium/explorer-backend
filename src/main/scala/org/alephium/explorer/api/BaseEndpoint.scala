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

import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import org.alephium.explorer.api.ApiError

trait BaseEndpoint {

  val baseEndpoint = endpoint.errorOut(
    oneOf[ApiError](
      statusMapping(StatusCode.NotFound, jsonBody[ApiError.NotFound].description("Not found")),
      statusMapping(StatusCode.BadRequest, jsonBody[ApiError.BadRequest].description("Bad request"))
    )
  )
}
