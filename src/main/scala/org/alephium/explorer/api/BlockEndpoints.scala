package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.json.circe._

import org.alephium.explorer.api.Schemas.avectorSchema
import org.alephium.explorer.api.model.BlockEntry

trait BlockEndpoints {

  val getBlockById: Endpoint[String, String, BlockEntry, Nothing] =
    endpoint.get
      .in("blocks" / path[String]("blockID"))
      .out(jsonBody[BlockEntry])
      .errorOut(plainBody[String])

}
