// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq

import sttp.model.StatusCode

import org.alephium.api.ApiError.NotFound
import org.alephium.explorer._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.model.Transaction
import org.alephium.explorer.persistence.DatabaseFixtureForAll

class TransactionServerSpec()
    extends AlephiumFutureSpec
    with DatabaseFixtureForAll
    with HttpServerFixture {

  private val server = new TransactionServer()

  override val routes = server.routes

  "transactions" should {
    "return an empty list on an empty database" in {
      Get("/transactions") check { response =>
        response.as[ArraySeq[Transaction]] is ArraySeq.empty
      }
    }

    "return not found for an unknown transaction id" in {
      val txId = transactionHashGen.sample.get
      Get(s"/transactions/${txId.value.toHexString}") check { response =>
        response.as[NotFound] is NotFound(txId.value.toHexString)
      }
    }

    "reject invalid unsigned transaction payloads" in {
      Post("/transactions/decode-unsigned-tx", """{"unsignedTx":"not-hex"}""") check { response =>
        response.code is StatusCode.BadRequest
      }
    }
  }
}
