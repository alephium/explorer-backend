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

import org.alephium.api.{alphJsonBody => jsonBody}
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.TokenId
import org.alephium.util.Duration

// scalastyle:off magic.number
trait AddressesEndpoints extends BaseEndpoint with QueryParams {

  def groupNum: Int

  //As defined in https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki#address-gap-limit
  private val gapLimit = 20

  private lazy val activeAddressesMaxSize: Int = groupNum * gapLimit

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

  val getTransactionsByAddressDEPRECATED
    : BaseEndpoint[(Address, Pagination), ArraySeq[Transaction]] =
    addressesEndpoint.get
      .in("transactions-DEPRECATED")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List transactions of a given address")

  val getTransactionsByAddress: BaseEndpoint[(Address, Pagination), ArraySeq[Transaction]] =
    addressesEndpoint.get
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List transactions of a given address")

  lazy val getTransactionsByAddresses
    : BaseEndpoint[(ArraySeq[Address], Pagination), ArraySeq[Transaction]] =
    baseAddressesEndpoint.post
      .in(jsonBody[ArraySeq[Address]].validate(Validator.maxSize(activeAddressesMaxSize)))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List transactions for given addresses")

  val getTransactionsByAddressTimeRanged
    : BaseEndpoint[(Address, TimeInterval, Pagination), ArraySeq[Transaction]] =
    addressesEndpoint.get
      .in("timeranged-transactions")
      .in(timeIntervalQuery)
      .in(paginator(Pagination.thousand))
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List transactions of a given address within a time-range")

  val getTotalTransactionsByAddress: BaseEndpoint[Address, Int] =
    addressesEndpoint.get
      .in("total-transactions")
      .out(jsonBody[Int])
      .description("Get total transactions of a given address")

  val addressUnconfirmedTransactions: BaseEndpoint[Address, ArraySeq[TransactionLike]] =
    addressesEndpoint.get
      .in("unconfirmed-transactions")
      .out(jsonBody[ArraySeq[TransactionLike]])
      .description("List unconfirmed transactions of a given address")

  val getAddressBalance: BaseEndpoint[Address, AddressBalance] =
    addressesEndpoint.get
      .in("balance")
      .out(jsonBody[AddressBalance])
      .description("Get address balance")

  val listAddressTokens: BaseEndpoint[Address, ArraySeq[TokenId]] =
    addressesTokensEndpoint.get
      .out(jsonBody[ArraySeq[TokenId]])
      .description("List address tokens")

  val getAddressTokenBalance: BaseEndpoint[(Address, TokenId), AddressBalance] =
    addressesTokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("balance")
      .out(jsonBody[AddressBalance])
      .description("Get address balance of given token")

  val listAddressTokenTransactions
    : BaseEndpoint[(Address, TokenId, Pagination), ArraySeq[Transaction]] =
    addressesTokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List address tokens")

  lazy val areAddressesActive: BaseEndpoint[ArraySeq[Address], ArraySeq[Boolean]] =
    baseEndpoint
      .tag("Addresses")
      .in("addresses-active")
      .post
      .in(jsonBody[ArraySeq[Address]].validate(Validator.maxSize(activeAddressesMaxSize)))
      .out(jsonBody[ArraySeq[Boolean]])
      .description("Are the addresses active (at least 1 transaction)")

  val exportTransactionsCsvByAddress
    : BaseEndpoint[(Address, TimeInterval), (String, ReadStream[Buffer])] =
    addressesEndpoint.get
      .in("export-transactions")
      .in("csv")
      .in(timeIntervalWithMaxQuery(oneYear))
      .out(header[String](HeaderNames.ContentDisposition))
      .out(streamTextBody(VertxStreams)(TextCsv()))

  private case class TextCsv() extends CodecFormat {
    override val mediaType: MediaType = MediaType.TextCsv
  }
}
