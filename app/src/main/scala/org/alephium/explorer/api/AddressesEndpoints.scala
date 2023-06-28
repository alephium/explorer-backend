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
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.util.Duration

// scalastyle:off magic.number
trait AddressesEndpoints extends BaseEndpoint with QueryParams {

  def groupNum: Int

  //As defined in https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki#address-gap-limit
  private val gapLimit = 20

  private lazy val usedAddressesMaxSize: Int = groupNum * gapLimit

  private val oneYear = Duration.ofDaysUnsafe(365)

  private val baseAddressesEndpoint =
    baseEndpoint
      .tag("Addresses")
      .in("addresses")

  private val addressesEndpoint =
    baseAddressesEndpoint
      .in(path[Address]("address")(Codecs.explorerAddressTapirCodec))

  private val addressesTokensEndpoint =
    baseEndpoint
      .tag("Addresses")
      .in("addresses")
      .in(path[Address]("address")(Codecs.explorerAddressTapirCodec))
      .in("tokens")

  val getAddressInfo: BaseEndpoint[Address, AddressInfo] =
    addressesEndpoint.get
      .out(jsonBody[AddressInfo])
      .description("Get address information")

  val getTransactionsByAddress: BaseEndpoint[(Address, Pagination), ArraySeq[Transaction]] =
    addressesEndpoint.get
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List transactions of a given address")

  lazy val getTransactionsByAddresses
    : BaseEndpoint[(ArraySeq[Address], Pagination), ArraySeq[Transaction]] =
    baseAddressesEndpoint.post
      .in(jsonBody[ArraySeq[Address]].validate(Validator.maxSize(usedAddressesMaxSize)))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List transactions for given addresses")

  val getTransactionsByAddressTimeRanged
    : BaseEndpoint[(Address, TimeInterval, Pagination), ArraySeq[Transaction]] =
    addressesEndpoint.get
      .in("timeranged-transactions")
      .in(timeIntervalQuery)
      .in(paginator(maxLimit = Pagination.thousand))
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List transactions of a given address within a time-range")

  val getTotalTransactionsByAddress: BaseEndpoint[Address, Int] =
    addressesEndpoint.get
      .in("total-transactions")
      .out(jsonBody[Int])
      .description("Get total transactions of a given address")

  val addressMempoolTransactions: BaseEndpoint[Address, ArraySeq[MempoolTransaction]] =
    addressesEndpoint.get
      .in("mempool")
      .in("transactions")
      .out(jsonBody[ArraySeq[MempoolTransaction]])
      .description("List mempool transactions of a given address")

  val getAddressBalance: BaseEndpoint[Address, AddressBalance] =
    addressesEndpoint.get
      .in("balance")
      .out(jsonBody[AddressBalance])
      .description("Get address balance")

  val listAddressTokens: BaseEndpoint[(Address, Pagination), ArraySeq[TokenId]] =
    addressesTokensEndpoint.get
      .out(jsonBody[ArraySeq[TokenId]])
      .in(paginator(defaultLimit = 100))
      .description("List address tokens")

  val getAddressTokenBalance: BaseEndpoint[(Address, TokenId), AddressTokenBalance] =
    addressesTokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("balance")
      .out(jsonBody[AddressTokenBalance])
      .description("Get address balance of given token")

  val listAddressTokensBalance: BaseEndpoint[(Address, Pagination), ArraySeq[AddressTokenBalance]] =
    addressesEndpoint.get
      .in("tokens-balance")
      .in(pagination)
      .out(jsonBody[ArraySeq[AddressTokenBalance]])
      .description("Get address tokens with balance")

  val listAddressTokenTransactions
    : BaseEndpoint[(Address, TokenId, Pagination), ArraySeq[Transaction]] =
    addressesTokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List address tokens")

  lazy val areAddressesActive: BaseEndpoint[ArraySeq[Address], ArraySeq[Boolean]] =
    baseAddressesEndpoint
      .tag("Addresses")
      .in("used")
      .post
      .in(jsonBody[ArraySeq[Address]].validate(Validator.maxSize(usedAddressesMaxSize)))
      .out(jsonBody[ArraySeq[Boolean]])
      .description("Are the addresses used (at least 1 transaction)")

  val exportTransactionsCsvByAddress
    : BaseEndpoint[(Address, TimeInterval), (String, ReadStream[Buffer])] =
    addressesEndpoint.get
      .in("export-transactions")
      .in("csv")
      .in(timeIntervalWithMaxQuery(oneYear))
      .out(header[String](HeaderNames.ContentDisposition))
      .out(streamTextBody(VertxStreams)(TextCsv()))

  val getAddressAmountHistory
    : BaseEndpoint[(Address, TimeInterval, IntervalType), (String, ReadStream[Buffer])] =
    addressesEndpoint.get
      .in("amount-history")
      .in(timeIntervalQuery)
      .in(intervalTypeQuery)
      .out(header[String](HeaderNames.ContentDisposition))
      .out(streamTextBody(VertxStreams)(CodecFormat.Json()))

  private case class TextCsv() extends CodecFormat {
    override val mediaType: MediaType = MediaType.TextCsv
  }
}
