package org.alephium.explorer.web

import sttp.tapir.server.akkahttp.AkkaHttpServerOptions

import org.alephium.explorer.api.DecodeFailureHandler

trait AkkaDecodeFailureHandler extends DecodeFailureHandler {
  implicit val akkaHttpServerOptions: AkkaHttpServerOptions = AkkaHttpServerOptions.default.copy(
    decodeFailureHandler = myDecodeFailureHandler
  )
}
