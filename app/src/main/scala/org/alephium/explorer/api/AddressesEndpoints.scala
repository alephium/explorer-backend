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
import org.alephium.protocol.PublicKey
import org.alephium.protocol.model.{Address, AddressLike, GroupIndex, TokenId}
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
      .in(path[AddressLike]("address")(Codecs.explorerAddressLikeTapirCodec))

  private val noGroupAddressesEndpoint =
    baseAddressesEndpoint
      .in(path[(Address, Option[GroupIndex])]("address")(Codecs.explorerAddressTapirCodec))
      .mapInDecode[Address] { case (address, groupIndex) =>
        groupIndex match {
          case Some(group) =>
            DecodeResult.Error(
              s"${address.toBase58}@${group.value}",
              new IllegalArgumentException(
                s"Group  notation `@group` not supported yet for this endpoint"
              )
            )
          case None =>
            DecodeResult.Value(address)
        }
      } { address =>
        (address, None)
      }
      .description("Address with group notation `@group` not supported yet for this endpoint")

  private val addressesTokensEndpoint =
    noGroupAddressesEndpoint
      .in("tokens")

  val getAddressInfo: BaseEndpoint[AddressLike, AddressInfo] =
    addressesLikeEndpoint.get
      .out(jsonBody[AddressInfo])
      .summary("Get address information")

  val getTransactionsByAddress: BaseEndpoint[(AddressLike, Pagination), ArraySeq[Transaction]] =
    addressesLikeEndpoint.get
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List transactions of a given address")

  // format: off
  lazy val getTransactionsByAddresses: BaseEndpoint[(ArraySeq[Address], Option[TimeStamp], Option[TimeStamp], Pagination), ArraySeq[Transaction]] =
    baseAddressesEndpoint.post
      .in(arrayBody[Address]("addresses", maxSizeAddresses))
      .in("transactions")
      .in(optionalTimeIntervalQuery)
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List transactions for given addresses")

  val getTransactionsByAddressTimeRanged: BaseEndpoint[(Address, TimeInterval, Pagination), ArraySeq[Transaction]] =
    noGroupAddressesEndpoint.get
      .in("timeranged-transactions")
      .in(timeIntervalQuery)
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List transactions of a given address within a time-range")
  // format: on

  val getTotalTransactionsByAddress: BaseEndpoint[AddressLike, Int] =
    addressesLikeEndpoint.get
      .in("total-transactions")
      .out(jsonBody[Int])
      .summary("Get total transactions of a given address")

  val getLatestTransactionInfo: BaseEndpoint[Address, TransactionInfo] =
    noGroupAddressesEndpoint.get
      .in("latest-transaction")
      .out(jsonBody[TransactionInfo])
      .summary("Get latest transaction information of a given address")

  val addressMempoolTransactions: BaseEndpoint[Address, ArraySeq[MempoolTransaction]] =
    noGroupAddressesEndpoint.get
      .in("mempool")
      .in("transactions")
      .out(jsonBody[ArraySeq[MempoolTransaction]])
      .summary("List mempool transactions of a given address")

  val getAddressBalance: BaseEndpoint[AddressLike, AddressBalance] =
    addressesLikeEndpoint.get
      .in("balance")
      .out(jsonBody[AddressBalance])
      .summary("Get address balance")

  val listAddressTokens: BaseEndpoint[(AddressLike, Pagination), ArraySeq[TokenId]] =
    addressesLikeEndpoint.get
      .in("tokens")
      .out(jsonBody[ArraySeq[TokenId]])
      .in(paginator(limit = 100))
      .summary("List address tokens")
      .deprecated()

  val getAddressTokenBalance: BaseEndpoint[(Address, TokenId), AddressTokenBalance] =
    addressesTokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("balance")
      .out(jsonBody[AddressTokenBalance])
      .summary("Get address balance of given token")

  val listAddressTokensBalance: BaseEndpoint[(Address, Pagination), ArraySeq[AddressTokenBalance]] =
    noGroupAddressesEndpoint.get
      .in("tokens-balance")
      .in(pagination)
      .out(jsonBody[ArraySeq[AddressTokenBalance]])
      .summary("Get address tokens with balance")

  val listAddressTokenTransactions
      : BaseEndpoint[(Address, TokenId, Pagination), ArraySeq[Transaction]] =
    addressesTokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List address tokens")

  lazy val areAddressesActive: BaseEndpoint[ArraySeq[Address], ArraySeq[Boolean]] =
    baseAddressesEndpoint
      .tag("Addresses")
      .in("used")
      .post
      .in(arrayBody[Address]("addresses", maxSizeAddresses))
      .out(jsonBody[ArraySeq[Boolean]])
      .summary("Are the addresses used (at least 1 transaction)")

  lazy val exportTransactionsCsvByAddress
      : BaseEndpoint[(Address, TimeInterval), (String, ReadStream[Buffer])] =
    noGroupAddressesEndpoint.get
      .in("export-transactions")
      .in("csv")
      .in(timeIntervalWithMaxQuery(maxTimeIntervalExportTxs))
      .out(header[String](HeaderNames.ContentDisposition))
      .out(streamTextBody(VertxStreams)(TextCsv()))

      //format: off
  val getAddressAmountHistoryDEPRECATED: BaseEndpoint[(Address, TimeInterval, IntervalType), (String, ReadStream[Buffer]) ] =
    noGroupAddressesEndpoint.get
      .in("amount-history-DEPRECATED")
      .in(timeIntervalQuery)
      .in(intervalTypeQuery)
      .out(header[String](HeaderNames.ContentDisposition))
      .out(streamTextBody(VertxStreams)(CodecFormat.Json()))
      .deprecated()
      //format: on

  val getAddressAmountHistory: BaseEndpoint[(Address, TimeInterval, IntervalType), AmountHistory] =
    noGroupAddressesEndpoint.get
      .in("amount-history")
      .in(timeIntervalQuery)
      .in(intervalTypeQuery)
      .out(jsonBody[AmountHistory])

  val getPublicKey: BaseEndpoint[Address, PublicKey] =
    noGroupAddressesEndpoint.get
      .in("public-key")
      .out(jsonBody[PublicKey])
      .summary(
        "Get public key of p2pkh addresses, the address needs to have at least one input."
      )

  private case class TextCsv() extends CodecFormat {
    override val mediaType: MediaType = MediaType.TextCsv
  }
}
