// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.docs

import scala.collection.immutable.ArraySeq

import sttp.apispec._
import sttp.apispec.openapi.OpenAPI
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

import org.alephium.explorer.api._
import org.alephium.explorer.api.model.Pagination

trait Documentation
    extends BlockEndpoints
    with TransactionEndpoints
    with AddressesEndpoints
    with InfosEndpoints
    with ChartsEndpoints
    with TokensEndpoints
    with EventsEndpoints
    with MempoolEndpoints
    with ContractsEndpoints
    with MarketEndpoints
    with UtilsEndpoints
    with OpenAPIDocsInterpreter {

  def currencies: ArraySeq[String]

  // scalastyle:off method.length
  def docs: OpenAPI = addComponents(
    toOpenAPI(
      List(
        listBlocks,
        getBlockByHash,
        getBlockTransactions,
        getTransactionById,
        getAddressInfo,
        getTransactionsByAddress,
        getTransactionsByAddresses,
        getTransactionsByAddressTimeRanged,
        getTotalTransactionsByAddress,
        getLatestTransactionInfo,
        addressMempoolTransactions,
        getAddressBalance,
        listAddressTokens,
        listAddressTokenTransactions,
        getAddressTokenBalance,
        getPublicKey,
        listAddressTokensBalance,
        areAddressesActive,
        exportTransactionsCsvByAddress,
        getAddressAmountHistory,
        getInfos,
        getHeights,
        listMempoolTransactions,
        listTokens,
        listTokenTransactions,
        listTokenAddresses,
        listTokenSupply,
        listTokenInfo,
        listFungibleTokenMetadata,
        listNFTMetadata,
        listNFTCollectionMetadata,
        getTotalSupply,
        getCirculatingSupply,
        getReservedSupply,
        getLockedSupply,
        getTotalTransactions,
        getAverageBlockTime,
        getAlphHolders,
        getTokenHolders,
        getHashrates,
        getAllChainsTxCount,
        getPerChainTxCount,
        getEventsByTxId,
        getEventsByContractAddress,
        getEventsByContractAndInputAddress,
        getContractInfo,
        getParentAddress,
        getSubContracts,
        getPrices,
        getPriceChart,
        sanityCheck,
        changeGlobalLogLevel,
        changeLogConfig
      ),
      "Alephium Explorer API",
      "1.0"
    )
  )

  // Expose some variables to the openAPI file
  // scalastyle:off method.length
  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private def addComponents(openApi: OpenAPI): OpenAPI =
    openApi.components(
      openApi.components.get
        .addSchema(
          "PaginationLimitDefault",
          Schema(
            `type` = Some(List(SchemaType.Integer)),
            `enum` = Some(
              List(
                ExampleSingleValue(Pagination.defaultLimit),
                ExampleSingleValue(Pagination.futureDefaultLimit)
              )
            )
          )
        )
        .addSchema(
          "PaginationLimitMax",
          Schema(
            `type` = Some(List(SchemaType.Integer)),
            `enum` = Some(
              List(
                ExampleSingleValue(Pagination.maxLimit),
                ExampleSingleValue(Pagination.futureMaxLimit)
              )
            )
          )
        )
        .addSchema(
          "PaginationPageDefault",
          Schema(
            `type` = Some(List(SchemaType.Integer)),
            `enum` = Some(
              List(
                ExampleSingleValue(Pagination.defaultPage)
              )
            )
          )
        )
        .addSchema(
          "MaxSizeTokens",
          Schema(
            `type` = Some(List(SchemaType.Integer)),
            `enum` = Some(List(ExampleSingleValue(maxSizeTokens)))
          )
        )
        .addSchema(
          "MaxSizeAddressesForTokens",
          Schema(
            `type` = Some(List(SchemaType.Integer)),
            `enum` = Some(List(ExampleSingleValue(maxSizeAddressesForTokens)))
          )
        )
        .addSchema(
          "MaxSizeAddresses",
          Schema(
            `type` = Some(List(SchemaType.Integer)),
            `enum` = Some(List(ExampleSingleValue(maxSizeAddresses)))
          )
        )
        .addSchema(
          "Currencies",
          Schema(
            `type` = Some(List(SchemaType.String)),
            enum = Some(currencies.map { name => ExampleSingleValue(name) }.toList)
          )
        )
    )
}
