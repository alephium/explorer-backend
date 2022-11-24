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

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.{alphJsonBody => jsonBody}
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.TokenId

// scalastyle:off magic.number
object AddressesEndpoints extends BaseEndpoint with QueryParams {

  //As defined in https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki#address-gap-limit
  private val gapLimit = 20

  private def activeAddressesMaxSize(groupNum: Int): Int =
    groupNum * gapLimit

  private def addressesEndpoint =
    baseEndpoint
      .tag("Addresses")
      .in("addresses")

  private def addressesTokensEndpoint =
    baseEndpoint
      .tag("Addresses")
      .in("addresses")
      .in(path[Address]("address")(Codecs.explorerAddressTapirCodec))
      .in("tokens")

  def getAddressInfo: BaseEndpoint[Address, AddressInfo] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.explorerAddressTapirCodec))
      .out(jsonBody[AddressInfo])
      .description("Get address information")

  def getTransactionsByAddressDEPRECATED
    : BaseEndpoint[(Address, Pagination), ArraySeq[Transaction]] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.explorerAddressTapirCodec))
      .in("transactions-DEPRECATED")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List transactions of a given address")

  def getTransactionsByAddress: BaseEndpoint[(Address, Pagination), ArraySeq[Transaction]] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.explorerAddressTapirCodec))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List transactions of a given address")

  def getTransactionsByAddresses(
      groupNum: Int): BaseEndpoint[(ArraySeq[Address], Pagination), ArraySeq[Transaction]] =
    addressesEndpoint.post
      .in(jsonBody[ArraySeq[Address]].validate(Validator.maxSize(activeAddressesMaxSize(groupNum))))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List transactions for given addresses")

  def getTransactionsByAddressTimeRanged
    : BaseEndpoint[(Address, TimeInterval, Pagination), ArraySeq[Transaction]] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.explorerAddressTapirCodec))
      .in("timeranged-transactions")
      .in(timeIntervalQuery)
      .in(paginator(Pagination.thousand))
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List transactions of a given address within a time-range")

  def getTotalTransactionsByAddress: BaseEndpoint[Address, Int] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.explorerAddressTapirCodec))
      .in("total-transactions")
      .out(jsonBody[Int])
      .description("Get total transactions of a given address")

  def addressUnconfirmedTransactions: BaseEndpoint[Address, ArraySeq[TransactionLike]] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.explorerAddressTapirCodec))
      .in("unconfirmed-transactions")
      .out(jsonBody[ArraySeq[TransactionLike]])
      .description("List unconfirmed transactions of a given address")

  def getAddressBalance: BaseEndpoint[Address, AddressBalance] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.explorerAddressTapirCodec))
      .in("balance")
      .out(jsonBody[AddressBalance])
      .description("Get address balance")

  def listAddressTokens: BaseEndpoint[Address, ArraySeq[TokenId]] =
    addressesTokensEndpoint.get
      .out(jsonBody[ArraySeq[TokenId]])
      .description("List address tokens")

  def getAddressTokenBalance: BaseEndpoint[(Address, TokenId), AddressBalance] =
    addressesTokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("balance")
      .out(jsonBody[AddressBalance])
      .description("Get address balance of given token")

  def listAddressTokenTransactions
    : BaseEndpoint[(Address, TokenId, Pagination), ArraySeq[Transaction]] =
    addressesTokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List address tokens")

  def areAddressesActive(groupNum: Int): BaseEndpoint[ArraySeq[Address], ArraySeq[Boolean]] =
    baseEndpoint
      .tag("Addresses")
      .in("addresses-active")
      .post
      .in(jsonBody[ArraySeq[Address]].validate(Validator.maxSize(activeAddressesMaxSize(groupNum))))
      .out(jsonBody[ArraySeq[Boolean]])
      .description("Are the addresses active (at least 1 transaction)")
}
