package org.alephium.explorer.persistence.db

import scala.jdk.CollectionConverters._
import scala.util.Random

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

trait DatabaseFixture {

  private val config = ConfigFactory
    .parseMap(
      Map(
        ("db.db.url", s"jdbc:h2:mem:${Random.nextString(5)};DB_CLOSE_DELAY=-1")
      ).view.mapValues(ConfigValueFactory.fromAnyRef).toMap.asJava)
    .withFallback(ConfigFactory.load())

  val databaseConfig: DatabaseConfig[JdbcProfile] =
    DatabaseConfig.forConfig[JdbcProfile]("db", config)
}
