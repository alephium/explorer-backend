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

  it should "IntervalType" in {
    check[IntervalType](IntervalType.Hourly, s""""hourly"""")
    check[IntervalType](IntervalType.Daily, s""""daily"""")
  }

  it should "Transaction" in {
    forAll(transactionGen) { tx =>
      val expected = s"""
       |{
       |  "hash": "${tx.hash.value.toHexString}",
       |  "blockHash": "${tx.blockHash}",
       |  "timestamp": ${tx.timestamp.millis},
       |  "inputs": [],
       |  "outputs": [],
       |  "gasAmount": ${tx.gasAmount},
       |  "gasPrice": "${tx.gasPrice}"
       |}""".stripMargin
      check(tx, expected)
    }
  }

  it should "ConfirmedTransaction" in {
    forAll(transactionGen) { tx =>
      val expected = s"""
       |{
       |  "type": "Confirmed",
       |  "hash": "${tx.hash.value.toHexString}",
       |  "blockHash": "${tx.blockHash}",
       |  "timestamp": ${tx.timestamp.millis},
       |  "inputs": [],
       |  "outputs": [],
       |  "gasAmount": ${tx.gasAmount},
       |  "gasPrice": "${tx.gasPrice}"
       |}""".stripMargin
      check(ConfirmedTransaction.from(tx), expected)
    }
  }

  it should "Output.Ref" in {
    forAll(outputRefGen) { outputRef =>
      val expected = s"""
     |{
     |  "hint": ${outputRef.hint},
     |  "key": "${outputRef.key.toHexString}"
     |}""".stripMargin
      check(outputRef, expected)
    }
  }

  it should "Output" in {
    forAll(outputGen) { output =>
      val expected = s"""
     |{
     |  "hint": ${output.hint},
     |  "key": "${output.key.toHexString}",
     |  "amount": "${output.amount}",
     |  "address": "${output.address}"
     |  ${output.lockTime.map(lockTime => s""","lockTime": ${lockTime.millis}""").getOrElse("")}
     |  ${output.spent.map(spent => s""","spent": "${spent.value.toHexString}"""").getOrElse("")}
     |}""".stripMargin
      check(output, expected)
    }
  }

  it should "Input" in {
    forAll(inputGen) { input =>
      val expected = s"""
     |{
     |  "outputRef": ${write(input.outputRef)},
     |  ${input.unlockScript.map(script => s""""unlockScript": "${script}",""").getOrElse("")}
     |  "txHashRef": "${input.txHashRef.value.toHexString}",
     |  "address": "${input.address}",
     |  "amount": "${input.amount}"
     |}""".stripMargin
      check(input, expected)
    }
  }

  it should "UInput" in {
    forAll(uinputGen) { uinput =>
      val expected = uinput.unlockScript match {
        case None =>
          s"""
          |{
          |  "outputRef": ${write(uinput.outputRef)}
          |}""".stripMargin
        case Some(unlockScript) =>
          s"""
          |{
          |  "outputRef": ${write(uinput.outputRef)},
          |  "unlockScript": "${unlockScript}"
          |}""".stripMargin
      }
      check(uinput, expected)
    }
  }

  it should "UnconfirmedTx" in {
    forAll(utransactionGen) { utx =>
      val expected = s"""
     |{
     |  "type": "Unconfirmed",
     |  "hash": "${utx.hash.value.toHexString}",
     |  "chainFrom": ${utx.chainFrom.value},
     |  "chainTo": ${utx.chainTo.value},
     |  "inputs": ${write(utx.inputs)},
     |  "outputs": ${write(utx.outputs)},
     |  "gasAmount": ${utx.gasAmount},
     |  "gasPrice": "${utx.gasPrice}"
     |}""".stripMargin
      check(utx, expected)
    }
  }

  it should "ExplorerInfo" in {
    val expected = s"""
     |{
     |  "releaseVersion": "1.2.3",
     |  "commit": "b96f64ff"
     |}""".stripMargin
    check(ExplorerInfo("1.2.3", "b96f64ff"), expected)
  }
}
