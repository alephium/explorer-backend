// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import org.scalacheck.Gen
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer._
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixtureForAll
import org.alephium.explorer.service._
import org.alephium.protocol.model.TokenId

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
}
