package org.alephium.explorer

import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.ScalatestRouteTest

import org.alephium.util.AlephiumSpec

class ApplicationSpec() extends AlephiumSpec with ScalatestRouteTest {

  val app: Application = new Application {
    override val port: Int                                   = 8888
    override implicit val system: ActorSystem                = ActorSystem("ApplicationSpec")
    override implicit val executionContext: ExecutionContext = system.dispatcher
  }

  val routes = app.route

  app.start

  it should "get a block by its id" in {
    val id = "myId"
    Get(s"/blocks/$id") ~> routes ~> check {
      responseAs[String] is """{"hash":"myId","timestamp":0,"chainFrom":0,"chainTo":0,"height":0,"deps":[]}"""
    }
  }

  app.stop
}
