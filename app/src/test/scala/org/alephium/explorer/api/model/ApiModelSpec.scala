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

package org.alephium.explorer.api.model

import org.alephium.explorer.{AlephiumSpec, Generators}
import org.alephium.json.Json._

class ApiModelSpec() extends AlephiumSpec with Generators {

  def check[T: Reader: Writer](data: T, jsonRaw: String) = {
    write(data) is jsonRaw.filterNot(_.isWhitespace)
    read[T](jsonRaw) is data
  }

  it should "Transaction" in {
    forAll(transactionGen) { tx =>
      val expected = s"""
       |{
       |  "type": "confirmed",
       |  "hash": "${tx.hash.value.toHexString}",
       |  "blockHash": "${tx.blockHash}",
       |  "timestamp": ${tx.timestamp.millis},
       |  "inputs": [],
       |  "outputs": [],
       |  "startGas": ${tx.startGas},
       |  "gasPrice": "${tx.gasPrice}"
       |}""".stripMargin
      check(tx, expected)
    }
  }

  it should "UTransaction" in {
    forAll(utransactionGen) { utx =>
      val expected = s"""
     |{
     |  "type": "unconfirmed",
     |  "hash": "${utx.hash.value.toHexString}",
     |  "chainFrom": ${utx.chainFrom.value},
     |  "chainTo": ${utx.chainTo.value},
     |  "inputs": [],
     |  "outputs": [],
     |  "startGas": ${utx.startGas},
     |  "gasPrice": "${utx.gasPrice}"
     |}""".stripMargin
      check(utx, expected)
    }
  }
}
