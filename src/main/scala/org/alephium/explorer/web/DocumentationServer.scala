package org.alephium.explorer.web

import scala.concurrent.Future

import akka.http.scaladsl.server.Route
import sttp.tapir.{endpoint, plainBody, stringToPath}
import sttp.tapir.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.server.akkahttp.RichAkkaHttpEndpoint

import org.alephium.explorer.docs.Documentation

class DocumentationServer extends Server with Documentation {

  val route: Route =
    endpoint.get
      .in("openapi.yaml")
      .out(plainBody[String])
      .toRoute(_ => Future.successful(Right(docs.toYaml)))
}
