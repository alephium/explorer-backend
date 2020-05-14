package org.alephium.explorer.docs

import sttp.tapir.docs.openapi._
import sttp.tapir.openapi.OpenAPI

import org.alephium.explorer.api.BlockEndpoints

trait Documentation extends BlockEndpoints {
  val docs: OpenAPI = List(
    getBlockById
  ).toOpenAPI("Alephium Explorer API", "1.0")
}
