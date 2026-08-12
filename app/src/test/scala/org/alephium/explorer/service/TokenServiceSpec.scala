// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.concurrent.ScalaFutures
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.{DatabaseFixture, DBRunner}
import org.alephium.explorer.persistence.queries.ContractQueries

class TokenServiceSpec extends AlephiumFutureSpec with ScalaFutures {

  // A tiny DB executor so the regression stays easy to reproduce.
  private def smallPoolConfig(dbName: String): Config =
    ConfigFactory
      .parseMap(
        Map[String, AnyRef](
          "db.db.url"        -> s"jdbc:postgresql://localhost:5432/$dbName",
          "db.db.numThreads" -> Int.box(2),
          "db.db.queueSize"  -> Int.box(2)
        ).view.mapValues(ConfigValueFactory.fromAnyRef).toMap.asJava
      )
      .withFallback(ConfigFactory.load())

  "updateContractsMetadata" should {
    "not overflow the DB executor when many contracts are pending" in {
      val dbName = "tokenservicespec"

      DatabaseFixture.createDb(dbName)
      val setupDatabaseConfig = DatabaseFixture.createDatabaseConfig(dbName)
      DatabaseFixture.createTables()(setupDatabaseConfig)
      setupDatabaseConfig.db.close()

      implicit val databaseConfig: DatabaseConfig[PostgresProfile] =
        DatabaseConfig.forConfig[PostgresProfile]("db", smallPoolConfig(dbName))

      try {
        val groupIndex = groupIndexGen.sample.get
        val events     = ArraySeq.fill(50)(createEventGen(groupIndex).sample.get)
        DBRunner.run(ContractQueries.insertContractCreation(events, groupIndex)).futureValue

        val client = new EmptyBlockFlowClient {}

        TokenService.updateContractsMetadata(client).futureValue is ()

        DBRunner
          .run(ContractQueries.listContractWithoutInterfaceIdQuery())
          .futureValue is ArraySeq.empty

      } finally {
        databaseConfig.db.close()
        DatabaseFixture.dropDb(dbName)
        ()
      }
    }
  }
}
