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
  private val gapLimit = 20

  lazy val maxSizeAddresses: Int = groupNum * gapLimit

  def maxTimeIntervalExportTxs: Duration

  private val baseAddressesEndpoint =
    baseEndpoint
      .tag("Addresses")
      .in("addresses")

  private val addressesLikeEndpoint =
    baseAddressesEndpoint
      .in(path[ApiAddress]("address")(Codecs.explorerAddressTapirCodec))

  private val addressesLikeTokensEndpoint =
    baseAddressesEndpoint
      .in(path[ApiAddress]("address")(Codecs.explorerAddressTapirCodec))
      .in("tokens")

  val getAddressInfo: BaseEndpoint[ApiAddress, AddressInfo] =
    addressesLikeEndpoint.get
      .out(jsonBody[AddressInfo])
      .summary("Get address information")

  val getTransactionsByAddress: BaseEndpoint[(ApiAddress, Pagination), ArraySeq[Transaction]] =
    addressesLikeEndpoint.get
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List transactions of a given address")

  // format: off
  lazy val getTransactionsByAddresses: BaseEndpoint[(ArraySeq[ApiAddress], Option[TimeStamp], Option[TimeStamp], Pagination), ArraySeq[Transaction]] =
    baseAddressesEndpoint.post
      .in(arrayBody[ApiAddress]("addresses", maxSizeAddresses))
      .in("transactions")
      .in(optionalTimeIntervalQuery)
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List transactions for given addresses")

  val getTransactionsByAddressTimeRanged: BaseEndpoint[(ApiAddress, TimeInterval, Pagination), ArraySeq[Transaction]] =
    addressesLikeEndpoint.get
      .in("timeranged-transactions")
      .in(timeIntervalQuery)
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List transactions of a given address within a time-range")
  // format: on

  val getTotalTransactionsByAddress: BaseEndpoint[ApiAddress, Int] =
    addressesLikeEndpoint.get
      .in("total-transactions")
      .out(jsonBody[Int])
      .summary("Get total transactions of a given address")

  val getLatestTransactionInfo: BaseEndpoint[ApiAddress, TransactionInfo] =
    addressesLikeEndpoint.get
      .in("latest-transaction")
      .out(jsonBody[TransactionInfo])
      .summary("Get latest transaction information of a given address")

  val addressMempoolTransactions: BaseEndpoint[ApiAddress, ArraySeq[MempoolTransaction]] =
    addressesLikeEndpoint.get
      .in("mempool")
      .in("transactions")
      .out(jsonBody[ArraySeq[MempoolTransaction]])
      .summary("List mempool transactions of a given address")

  val getAddressBalance: BaseEndpoint[ApiAddress, AddressBalance] =
    addressesLikeEndpoint.get
      .in("balance")
      .out(jsonBody[AddressBalance])
      .summary("Get address balance")

  val listAddressTokens: BaseEndpoint[(ApiAddress, Pagination), ArraySeq[TokenId]] =
    addressesLikeTokensEndpoint.get
      .out(jsonBody[ArraySeq[TokenId]])
      .in(paginator(limit = 100))
      .summary("List address tokens")
      .deprecated()

  val getAddressTokenBalance: BaseEndpoint[(ApiAddress, TokenId), AddressTokenBalance] =
    addressesLikeTokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("balance")
      .out(jsonBody[AddressTokenBalance])
      .summary("Get address balance of given token")

  val listAddressTokensBalance
      : BaseEndpoint[(ApiAddress, Pagination), ArraySeq[AddressTokenBalance]] =
    addressesLikeEndpoint.get
      .in("tokens-balance")
      .in(pagination)
      .out(jsonBody[ArraySeq[AddressTokenBalance]])
      .summary("Get address tokens with balance")

  val listAddressTokenTransactions
      : BaseEndpoint[(ApiAddress, TokenId, Pagination), ArraySeq[Transaction]] =
    addressesLikeTokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List address tokens")

  lazy val areAddressesActive: BaseEndpoint[ArraySeq[ApiAddress], ArraySeq[Boolean]] =
    baseAddressesEndpoint
      .tag("Addresses")
      .in("used")
      .post
      .in(arrayBody[ApiAddress]("addresses", maxSizeAddresses))
      .out(jsonBody[ArraySeq[Boolean]])
      .summary("Are the addresses used (at least 1 transaction)")

  lazy val exportTransactionsCsvByAddress
      : BaseEndpoint[(ApiAddress, TimeInterval), (String, ReadStream[Buffer])] =
    addressesLikeEndpoint.get
      .in("export-transactions")
      .in("csv")
      .in(timeIntervalWithMaxQuery(maxTimeIntervalExportTxs))
      .out(header[String](HeaderNames.ContentDisposition))
      .out(streamTextBody(VertxStreams)(TextCsv()))

  val getAddressAmountHistory
      : BaseEndpoint[(ApiAddress, TimeInterval, IntervalType), AmountHistory] =
    addressesLikeEndpoint.get
      .in("amount-history")
      .in(timeIntervalQuery)
      .in(intervalTypeQuery)
      .out(jsonBody[AmountHistory])
      .deprecated()

  val getPublicKey: BaseEndpoint[ApiAddress, PublicKey] =
    addressesLikeEndpoint.get
      .in("public-key")
      .out(jsonBody[PublicKey])
      .summary("Use `/addresses/{address}/typed-public-key` instead")
      .deprecated()

  private case class TextCsv() extends CodecFormat {
    override val mediaType: MediaType = MediaType.TextCsv
  }
}
