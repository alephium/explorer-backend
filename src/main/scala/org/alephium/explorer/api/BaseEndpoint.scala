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
