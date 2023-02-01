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

package org.alephium.tools

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util._

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.hotspot.DefaultExports
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.config._
import org.alephium.explorer.util.ExecutionContextUtil

import sttp.model.Uri

import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.sideEffect
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.service.TransactionService

@SuppressWarnings(Array("org.wartremover.warts.GlobalExecutionContext"))
object Playground {
  implicit val ec: ExecutionContext = ExecutionContext.global
  def main(args: Array[String]): Unit = {

  val typesafeConfig = ConfigFactory.load()

  implicit val config: ExplorerConfig = ExplorerConfig.load(typesafeConfig)

  implicit val databaseConfig: DatabaseConfig[PostgresProfile] =
    DatabaseConfig.forConfig[PostgresProfile]("db", typesafeConfig)
      val blockflow = BlockFlowClient(
        Uri("localhost", 12973),
        4,
        None,
        true
      )

      val selfClique = await(blockflow.fetchSelfClique())
      println(s"${Console.RED}${Console.BOLD}*** selfClique ***\n\t${Console.RESET}${selfClique}")


      val tokens = await(TransactionService.listTokens(Pagination.unsafe(0,100)))
      println(s"${Console.RED}${Console.BOLD}*** tokens ***\n\t${Console.RESET}${tokens}")
      sys.exit(0)
  }

  def await[A](future:Future[A]):A = Await.result(future, 5.seconds)
}

