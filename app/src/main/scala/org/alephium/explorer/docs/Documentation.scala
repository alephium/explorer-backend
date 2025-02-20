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

  lazy val docs: OpenAPI = addComponents(
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
