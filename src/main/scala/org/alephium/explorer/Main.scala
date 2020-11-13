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

package org.alephium.explorer

import scala.concurrent.{Await, ExecutionContext}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.explorer.sideEffect
import org.alephium.util.Duration

object Main extends App with StrictLogging {

  logger.info("Starting Application")
  implicit val system: ActorSystem                = ActorSystem("Explorer")
  implicit val executionContext: ExecutionContext = system.dispatcher

  val config: Config = ConfigFactory.load()

  val blockflowUri: Uri = {
    val host = config.getString("blockflow.host")
    val port = config.getInt("blockflow.port")
    Uri(s"http://$host:$port")
  }

  val groupNum: Int = config.getInt("blockflow.groupNum")

  val port: Int    = config.getInt("explorer.port")
  val host: String = config.getString("explorer.host")

  val databaseConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile]("db")

  val app: Application =
    new Application(host, port, blockflowUri, groupNum, databaseConfig)

  sideEffect(
    scala.sys.addShutdownHook(Await.result(app.stop, Duration.ofSecondsUnsafe(10).asScala)))
}
