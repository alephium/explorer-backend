package org.alephium.explorer.docs

import sttp.tapir.docs.openapi.RichOpenAPIEndpoints
import sttp.tapir.openapi.OpenAPI

import org.alephium.explorer.api.{BlockEndpoints, TransactionEndpoints}

trait Documentation extends BlockEndpoints with TransactionEndpoints {
  val docs: OpenAPI = List(
    getBlockById,
    listBlocks,
    getTransactionById
  ).toOpenAPI("Alephium Explorer API", "1.0")
}
