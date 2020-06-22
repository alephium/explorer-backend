package org.alephium.explorer.docs

import sttp.tapir.docs.openapi.RichOpenAPIEndpoints
import sttp.tapir.openapi.OpenAPI

import org.alephium.explorer.api.{AddressesEndpoints, BlockEndpoints, TransactionEndpoints}

trait Documentation extends BlockEndpoints with TransactionEndpoints with AddressesEndpoints {
  val docs: OpenAPI = List(
    listBlocks,
    getBlockByHash,
    getTransactionById,
    getTransactionsByAddress
  ).toOpenAPI("Alephium Explorer API", "1.0")
}
