package org.alephium.explorer.api

import sttp.tapir.{endpoint, path, plainBody, stringToPath, Endpoint}
import sttp.tapir.json.circe.jsonBody

import org.alephium.explorer.api.Schemas.avectorSchema
import org.alephium.explorer.api.model.BlockEntry

trait BlockEndpoints {

  val getBlockById: Endpoint[String, String, BlockEntry, Nothing] =
    endpoint.get
      .in("blocks" / path[String]("blockID"))
      .out(jsonBody[BlockEntry])
      .errorOut(plainBody[String])

}
