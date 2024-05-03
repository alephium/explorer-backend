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

import sttp.apispec._
import sttp.apispec.openapi.OpenAPI
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

import org.alephium.explorer.api._
import org.alephium.explorer.config.ExplorerConfig

@SuppressWarnings(
  Array(
    "org.wartremover.warts.JavaSerializable",
    "org.wartremover.warts.Product",
    "org.wartremover.warts.Serializable"
  )
)
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

  def servicesConfig: ExplorerConfig.Services

  private lazy val blocks = List(
    listBlocks,
    getBlockByHash,
    getBlockTransactions
  )

  private lazy val transactions = List(
    getTransactionById
  )

  private lazy val addresses = List(
    getTransactionById,
    getAddressInfo,
    getTransactionsByAddress,
    getTransactionsByAddresses,
    getTransactionsByAddressTimeRanged,
    getTotalTransactionsByAddress,
    addressMempoolTransactions,
    getAddressBalance,
    listAddressTokens,
    listAddressTokenTransactions,
    getAddressTokenBalance,
    listAddressTokensBalance,
    areAddressesActive,
    exportTransactionsCsvByAddress,
    getAddressAmountHistoryDEPRECATED,
    getAddressAmountHistory
  )

  private lazy val infos = List(
    getInfos,
    getHeights,
    getAverageBlockTime
  )

  private lazy val tokens = List(
    listTokens,
    listTokenTransactions,
    listTokenAddresses,
    listTokenInfo,
    listFungibleTokenMetadata,
    listNFTMetadata,
    listNFTCollectionMetadata
  )

  private lazy val events = List(
    getEventsByTxId,
    getEventsByContractAddress,
    getEventsByContractAndInputAddress
  )

  private lazy val contracts = List(
    getParentAddress,
    getSubContracts
  )

  private lazy val mempool =
    if (servicesConfig.mempoolSync.enable) {
      List(
        listMempoolTransactions
      )
    } else {
      List.empty
    }

  private lazy val tokenSupply =
    if (servicesConfig.tokenSupply.enable) {
      List(
        listTokenSupply,
        getCirculatingSupply,
        getTotalSupply,
        getReservedSupply,
        getLockedSupply
      )
    } else {
      List.empty
    }

  private lazy val hashrate =
    if (servicesConfig.hashrate.enable) {
      List(getHashrates)
    } else {
      List.empty
    }

  private lazy val txHistory =
    if (servicesConfig.txHistory.enable) {
      List(getAllChainsTxCount, getPerChainTxCount)
    } else {
      List.empty
    }

  private lazy val market = List(
    getPrices,
    getPriceChart
  )

  private lazy val utils = List(
    sanityCheck,
    changeGlobalLogLevel,
    changeLogConfig
  )

  lazy val docs: OpenAPI = addComponents(
    toOpenAPI(
      blocks ++
        transactions ++
        mempool ++
        addresses ++
        infos ++
        tokenSupply ++
        tokens ++
        events ++
        contracts ++
        hashrate ++
        txHistory ++
        market ++
        utils,
      "Alephium Explorer API",
      "1.0"
    )
  )

  // Expose some variables to the openAPI file
  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private def addComponents(openApi: OpenAPI): OpenAPI =
    openApi.components(
      openApi.components.get
        .addSchema(
          "MaxSizeTokens",
          Schema(
            `type` = Some(SchemaType.Integer),
            `enum` = Some(List(ExampleSingleValue(maxSizeTokens)))
          )
        )
        .addSchema(
          "MaxSizeAddressesForTokens",
          Schema(
            `type` = Some(SchemaType.Integer),
            `enum` = Some(List(ExampleSingleValue(maxSizeAddressesForTokens)))
          )
        )
        .addSchema(
          "MaxSizeAddresses",
          Schema(
            `type` = Some(SchemaType.Integer),
            `enum` = Some(List(ExampleSingleValue(maxSizeAddresses)))
          )
        )
    )

}
