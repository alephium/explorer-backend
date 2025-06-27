// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import org.scalamock.scalatest.MockFactory
import sttp.model.StatusCode

import org.alephium.api.ApiError
import org.alephium.explorer._
import org.alephium.explorer.ConfigDefaults.groupSetting
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.model.LogbackValue
import org.alephium.explorer.cache.{BlockCache, TestBlockCache, TransactionCache}
import org.alephium.explorer.config.BootMode
import org.alephium.explorer.persistence.{Database, DatabaseFixtureForAll}
import org.alephium.explorer.service._
import org.alephium.json.Json

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class UtilsServerSpec()
    extends AlephiumActorSpecLike
    with DatabaseFixtureForAll
    with HttpServerFixture
    with MockFactory {

  implicit val blockFlowClient: BlockFlowClient = mock[BlockFlowClient]
  implicit val blockCache: BlockCache           = TestBlockCache()
  implicit val transactionCache: TransactionCache = TransactionCache(
    new Database(BootMode.ReadWrite)
  )

  val utilsServer =
    new UtilsServer()

  val routes = utilsServer.routes

  "update global loglevel" in {
    List("TRACE", "DEBUG", "INFO", "WARN", "ERROR").foreach { level =>
      Put(s"/utils/update-global-loglevel", level) check { response =>
        response.code is StatusCode.Ok
      }
    }
    List("Trace", "debug", "yop").foreach { level =>
      Put(s"/utils/update-global-loglevel", level) check { response =>
        response.as[ApiError.BadRequest] is ApiError.BadRequest(
          s"""Invalid value for: body (expected value to be one of (TRACE, DEBUG, INFO, WARN, ERROR), but got: "$level")"""
        )
      }
    }
  }

  "update logback values" in {

    val logbackValues: Seq[LogbackValue] = Seq(
      LogbackValue("org.test", LogbackValue.Level.Debug),
      LogbackValue("yop", LogbackValue.Level.Trace)
    )

    Put(s"/utils/update-log-config", Json.write(logbackValues)) check { response =>
      response.code is StatusCode.Ok
    }

    val json = """
    [
      {
        "name": "foo",
        "level": "boo"
      }
    ]
    """

    Put(s"/utils/update-log-config", json) check { response =>
      response.as[ApiError.BadRequest] is ApiError.BadRequest(
        s"Invalid value for: body (Cannot decode level, expected one of: ArraySeq(TRACE, DEBUG, INFO, WARN, ERROR) at index 55: decoding failure)"
      )
    }
  }

  trait Fixture {}
}
