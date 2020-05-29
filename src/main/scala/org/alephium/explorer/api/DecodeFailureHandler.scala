package org.alephium.explorer.api

import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.{DecodeFailureHandling, ServerDefaults}

import org.alephium.explorer.api.ApiError

trait DecodeFailureHandler {

  private val failureOutput: EndpointOutput[(StatusCode, ApiError)] =
    statusCode.and(jsonBody[ApiError])

  private def myFailureResponse(statusCode: StatusCode, message: String): DecodeFailureHandling =
    DecodeFailureHandling.response(failureOutput)(
      (statusCode, ApiError.BadRequest(message))
    )

  val myDecodeFailureHandler = ServerDefaults.decodeFailureHandler.copy(
    response = myFailureResponse
  )
}
