// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestKitBase}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll

trait AlephiumActorSpecLike
    extends AlephiumFutureSpec
    with TestKitBase
    with ImplicitSender
    with BeforeAndAfterAll {

  val name: String = this.getClass.getSimpleName

  implicit lazy val system: ActorSystem =
    ActorSystem(name, ConfigFactory.parseString(AlephiumActorSpec.config))

  implicit override lazy val executionContext: ExecutionContext = system.dispatcher

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}

object AlephiumActorSpec {
  val config: String =
    """
      |akka {
      |  loglevel = "DEBUG"
      |  loggers = ["akka.event.slf4j.Slf4jLogger"]
      |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
      |
      |  io.tcp.trace-logging = off
      |
      |  actor {
      |    debug {
      |      unhandled = on
      |    }
      |
      |    guardian-supervisor-strategy = "org.alephium.util.DefaultStrategy"
      |  }
      |}
    """.stripMargin
}
