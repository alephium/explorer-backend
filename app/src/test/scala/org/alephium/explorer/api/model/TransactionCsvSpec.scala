// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import scala.collection.immutable.ArraySeq
import scala.io.{Codec, Source}
import scala.util.Using

import org.scalatest.OptionValues

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.AlephiumSpec
import org.alephium.json.Json._
import org.alephium.protocol.model.Address
import org.alephium.util.U256

class TransactionCsvSpec extends AlephiumSpec with OptionValues {
  val transaction = Using.resource(Source.fromResource("csv-contract-537.json")(Codec.UTF8)) {
    source => read[Transaction](source.mkString)
  }
  val wallet   = Address.fromBase58("1BZ5nen4sNEgvGh7LDM7hn7rDkaA7jbzWHpBMT6j89U3G").rightValue
  val contract = Address.fromBase58("2A7QeUr9z6qv8ywYX79jtYAnr8BtcYmFMCQ49ctVwL8z3").rightValue
  val hash     = "bdc1527ccd296d2f2676604c247ee864e13a3bfd8149d860a6538371f2235319"
  val date     = "2024-05-07T23:16:12.471Z"
  val senders  = s"$wallet-$contract"

  "Contract CSV exports" should {
    "retain the input address receiving ALPH in the transaction reported in issue 537" in {
      transaction.toCsv(
        ApiAddress.fromProtocol(contract),
        Map.empty
      ) is s"$date,-50,ALPH,$hash,$senders,$wallet\n"
      transaction.toCsv(
        ApiAddress.fromProtocol(wallet),
        Map.empty
      ) is s"$date,49.9956943,ALPH,$hash,$senders,$wallet\n"
    }

    "aggregate repeated outputs and retain a token recipient that also supplied inputs" in {
      val tx       = withTokens(6, 9, 95)
      val token    = tx.inputs(0).tokens.value(0).id
      val metadata = FungibleTokenMetadata(token, "TEST", "Test token", U256.One)
      tx.toCsv(ApiAddress.fromProtocol(contract), Map(token -> metadata)) is
        s"$date,-50,ALPH,$hash,$senders,$wallet\n$date,-0.5,TEST,$hash,$senders,$wallet\n"
    }

    "choose recipients independently for ALPH and each token" in {
      val tx    = withTokens(2, 3, 105)
      val token = tx.inputs(0).tokens.value(0).id
      tx.toCsv(ApiAddress.fromProtocol(contract), Map.empty) is
        s"$date,-50,ALPH,$hash,$senders,$wallet\n$date,5,${token.toHexString},$hash,$senders,$contract\n"
    }

    "exclude unchanged token balances and negative net change" in {
      val tx    = withTokens(4, 6, 90)
      val token = tx.inputs(0).tokens.value(0).id
      tx.toCsv(ApiAddress.fromProtocol(contract), Map.empty) is
        s"$date,-50,ALPH,$hash,$senders,$wallet\n$date,-10,${token.toHexString},$hash,$senders,\n"
    }
  }

  def withTokens(first: Int, second: Int, remaining: Int): Transaction = {
    val token               = transaction.inputs(1).tokens.value(0).id
    def tokens(amount: Int) = Some(ArraySeq(Token(token, U256.unsafe(amount))))
    transaction.copy(
      inputs = ArraySeq(
        transaction.inputs(0).copy(tokens = tokens(10)),
        transaction.inputs(1).copy(tokens = tokens(100))
      ),
      outputs = transaction.outputs.zip(ArraySeq(first, second, remaining)).map {
        case (output: AssetOutput, amount)    => output.copy(tokens = tokens(amount))
        case (output: ContractOutput, amount) => output.copy(tokens = tokens(amount))
      }
    )
  }
}
