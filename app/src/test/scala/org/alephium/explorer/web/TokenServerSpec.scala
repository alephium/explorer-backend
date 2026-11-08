// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.io.{Codec, Source}

import org.scalacheck.Gen
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import sttp.model.StatusCode

import org.alephium.api.OpenAPIWriters.openApiJson
import org.alephium.explorer._
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.{DatabaseFixtureForAll, DBRunner}
import org.alephium.explorer.persistence.model.TokenSupplySnapshotEntity
import org.alephium.explorer.persistence.schema.TokenSupplySnapshotSchema
import org.alephium.explorer.service._
import org.alephium.explorer.util.TestUtils._
import org.alephium.json.Json._
import org.alephium.protocol.model.TokenId
import org.alephium.util.{Duration, TimeStamp, U256}

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class TokenServerSpec()
    extends AlephiumFutureSpec
    with HttpServerFixture
    with DatabaseFixtureForAll {

  val tokenService = new EmptyTokenService {}
  val holdertokens = ArraySeq.from(Gen.listOf(holderInfoGen).sample.get)

  val holderService = new EmptyHolderService {
    override def getAlphHolders(pagination: Pagination)(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[HolderInfo]] = Future.successful(holdertokens)

    override def getTokenHolders(token: TokenId, pagination: Pagination)(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[HolderInfo]] = Future.successful(holdertokens)
  }

  val tokenServer =
    new TokenServer(tokenService, holderService)

  val routes = tokenServer.routes

  "return alph holders" in {
    Get(s"/tokens/holders/alph") check { response =>
      response.as[ArraySeq[HolderInfo]] is holdertokens
    }
  }

  "return token holders" in {
    Get(s"/tokens/holders/token/${tokenIdGen.sample.get.toHexString}") check { response =>
      response.as[ArraySeq[HolderInfo]] is holdertokens
    }
  }
  "return token supply newest first with pagination and decimal string amounts" in {
    val token = tokenIdGen.sample.get
    val older = TokenSupplySnapshotEntity(token, TimeStamp.unsafe(1000), U256.MaxValue)
    val newer = TokenSupplySnapshotEntity(token, TimeStamp.unsafe(2000), U256.Zero)
    DBRunner.run(TokenSupplySnapshotSchema.table ++= ArraySeq(older, newer)).futureValue
    Get(s"/tokens/${token.toHexString}/supply?page=1&limit=1") check { response =>
      response.code is StatusCode.Ok
      response.as[ArraySeq[TokenSupplySnapshot]] is ArraySeq(
        TokenSupplySnapshot(newer.timestamp, newer.total)
      )
    }
    Get(s"/tokens/${token.toHexString}/supply?page=2&limit=1") check { response =>
      response.as[ArraySeq[TokenSupplySnapshot]] is ArraySeq(
        TokenSupplySnapshot(older.timestamp, older.total)
      )
      val body = response.as[ujson.Value]
      body(0)("total").str is U256.MaxValue.toString
    }
    Get(s"/tokens/${tokenIdGen.sample.get.toHexString}/supply") check { response =>
      response.as[ArraySeq[TokenSupplySnapshot]] is ArraySeq.empty
    }
    Get(s"/tokens/${token.toHexString}/supply?page=0") check { response =>
      response.code is StatusCode.BadRequest
    }
    Get("/tokens/invalid-token/supply") check { response =>
      response.code is StatusCode.BadRequest
    }
  }

  "document the token supply endpoint and amount representation" in {
    val documentation =
      new DocumentationServer(Duration.ofDaysUnsafe(1), ArraySeq.empty)(GroupSetting(4))
    val actual =
      ujson.read(openApiJson(documentation.docs, dropAuth = false, truncateAddresses = true))
    val expected = using(Source.fromResource("explorer-backend-openapi.json")(Codec.UTF8)) {
      source =>
        ujson.read(source.mkString)
    }
    actual("paths")("/tokens/{token_id}/supply") is expected("paths")("/tokens/{token_id}/supply")
    actual("components")("schemas")("TokenSupplySnapshot") is expected("components")("schemas")(
      "TokenSupplySnapshot"
    )
  }

}
