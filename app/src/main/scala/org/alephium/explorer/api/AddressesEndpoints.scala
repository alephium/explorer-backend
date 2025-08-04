// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import scala.collection.immutable.ArraySeq

import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import sttp.model.{HeaderNames, MediaType}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.server.vertx.streams.VertxStreams

import org.alephium.api.Endpoints.jsonBody
import org.alephium.api.model.{Address => ApiAddress, TimeInterval}
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model._
import org.alephium.protocol.PublicKey
import org.alephium.protocol.model.TokenId
import org.alephium.util.{Duration, TimeStamp}

// scalastyle:off magic.number
trait AddressesEndpoints extends BaseEndpoint with QueryParams {

  def groupNum: Int

  // As defined in https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki#address-gap-limit
  private def gapLimit = 20

  def maxSizeAddresses: Int = groupNum * gapLimit

  def maxTimeIntervalExportTxs: Duration

  private def baseAddressesEndpoint =
    baseEndpoint
      .tag("Addresses")
      .in("addresses")

  private def addressesLikeEndpoint =
    baseAddressesEndpoint
      .in(path[ApiAddress]("address")(Codecs.explorerAddressTapirCodec))

  private def addressesLikeTokensEndpoint =
    baseAddressesEndpoint
      .in(path[ApiAddress]("address")(Codecs.explorerAddressTapirCodec))
      .in("tokens")

  def getAddressInfo: BaseEndpoint[ApiAddress, AddressInfo] =
    addressesLikeEndpoint.get
      .out(jsonBody[AddressInfo])
      .summary("Get address information")

  def getTransactionsByAddress: BaseEndpoint[(ApiAddress, Pagination), ArraySeq[Transaction]] =
    addressesLikeEndpoint.get
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List transactions of a given address")

  // format: off
  def getTransactionsByAddresses: BaseEndpoint[(ArraySeq[ApiAddress], Option[TimeStamp], Option[TimeStamp], Pagination), ArraySeq[Transaction]] =
    baseAddressesEndpoint.post
      .in(arrayBody[ApiAddress]("addresses", maxSizeAddresses))
      .in("transactions")
      .in(optionalTimeIntervalQuery)
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List transactions for given addresses")
      .deprecated()

  def getTransactionsByAddressTimeRanged: BaseEndpoint[(ApiAddress, TimeInterval, Pagination), ArraySeq[Transaction]] =
    addressesLikeEndpoint.get
      .in("timeranged-transactions")
      .in(timeIntervalQuery)
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List transactions of a given address within a time-range")
  // format: on

  def getTotalTransactionsByAddress: BaseEndpoint[ApiAddress, Int] =
    addressesLikeEndpoint.get
      .in("total-transactions")
      .out(jsonBody[Int])
      .summary("Get total transactions of a given address")

  def getLatestTransactionInfo: BaseEndpoint[ApiAddress, TransactionInfo] =
    addressesLikeEndpoint.get
      .in("latest-transaction")
      .out(jsonBody[TransactionInfo])
      .summary("Get latest transaction information of a given address")

  def addressMempoolTransactions: BaseEndpoint[ApiAddress, ArraySeq[MempoolTransaction]] =
    addressesLikeEndpoint.get
      .in("mempool")
      .in("transactions")
      .out(jsonBody[ArraySeq[MempoolTransaction]])
      .summary("List mempool transactions of a given address")

  def getAddressBalance: BaseEndpoint[ApiAddress, AddressBalance] =
    addressesLikeEndpoint.get
      .in("balance")
      .out(jsonBody[AddressBalance])
      .summary("Get address balance")

  def listAddressTokens: BaseEndpoint[(ApiAddress, Pagination), ArraySeq[TokenId]] =
    addressesLikeTokensEndpoint.get
      .out(jsonBody[ArraySeq[TokenId]])
      .in(paginator(limit = 100))
      .summary("List address tokens")
      .deprecated()

  def getAddressTokenBalance: BaseEndpoint[(ApiAddress, TokenId), AddressTokenBalance] =
    addressesLikeTokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("balance")
      .out(jsonBody[AddressTokenBalance])
      .summary("Get address balance of given token")

  def listAddressTokensBalance
      : BaseEndpoint[(ApiAddress, Pagination), ArraySeq[AddressTokenBalance]] =
    addressesLikeEndpoint.get
      .in("tokens-balance")
      .in(pagination)
      .out(jsonBody[ArraySeq[AddressTokenBalance]])
      .summary("Get address tokens with balance")

  def listAddressTokenTransactions
      : BaseEndpoint[(ApiAddress, TokenId, Pagination), ArraySeq[Transaction]] =
    addressesLikeTokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List address tokens")

  def areAddressesActive: BaseEndpoint[ArraySeq[ApiAddress], ArraySeq[Boolean]] =
    baseAddressesEndpoint
      .tag("Addresses")
      .in("used")
      .post
      .in(arrayBody[ApiAddress]("addresses", maxSizeAddresses))
      .out(jsonBody[ArraySeq[Boolean]])
      .summary("Are the addresses used (at least 1 transaction)")

  def exportTransactionsCsvByAddress
      : BaseEndpoint[(ApiAddress, TimeInterval), (String, ReadStream[Buffer])] =
    addressesLikeEndpoint.get
      .in("export-transactions")
      .in("csv")
      .in(timeIntervalWithMaxQuery(maxTimeIntervalExportTxs))
      .out(header[String](HeaderNames.ContentDisposition))
      .out(streamTextBody(VertxStreams)(TextCsv()))

  def getAddressAmountHistory
      : BaseEndpoint[(ApiAddress, TimeInterval, IntervalType), AmountHistory] =
    addressesLikeEndpoint.get
      .in("amount-history")
      .in(timeIntervalQuery)
      .in(intervalTypeQuery)
      .out(jsonBody[AmountHistory])
      .deprecated()

  def getPublicKey: BaseEndpoint[ApiAddress, PublicKey] =
    addressesLikeEndpoint.get
      .in("public-key")
      .out(jsonBody[PublicKey])
      .summary("Use `/addresses/{address}/typed-public-key` instead")
      .deprecated()

  private case class TextCsv() extends CodecFormat {
    override def mediaType: MediaType = MediaType.TextCsv
  }
}
