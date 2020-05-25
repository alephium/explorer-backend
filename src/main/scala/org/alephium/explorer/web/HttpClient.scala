package org.alephium.explorer.web

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Sink, Source}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder

import org.alephium.explorer.sideEffect

trait HttpClient {
  def request[A: Decoder](httpRequest: HttpRequest): Future[Either[String, A]]
}

object HttpClient {
  def apply(bufferSize: Int, overflowStrategy: OverflowStrategy)(
      implicit system: ActorSystem,
      executionContext: ExecutionContext
  ): HttpClient = new Impl(bufferSize, overflowStrategy)

  private class Impl(bufferSize: Int, overflowStrategy: OverflowStrategy)(
      implicit system: ActorSystem,
      executionContext: ExecutionContext
  ) extends HttpClient
      with FailFastCirceSupport {

    def request[A: Decoder](httpRequest: HttpRequest): Future[Either[String, A]] =
      requestResponse(httpRequest)
        .flatMap {
          case HttpResponse(code, _, entity, _) if code.isSuccess =>
            Unmarshal(entity)
              .to[Either[io.circe.Error, A]]
              .map(_.left.map(_.getMessage))
          case HttpResponse(code, _, entity, _) =>
            Unmarshal(entity)
              .to[String]
              .map(error => Left(s"Request failed with code $code and message: $error"))
        }
        .recover {
          case error =>
            Left(s"Unexpected error: $error")
        }

    // Inspired from: https://doc.akka.io/docs/akka-http/current/client-side/host-level.html#using-a-host-connection-pool
    private val superPoolFlow = Http().superPool[Promise[HttpResponse]]()
    private val queue =
      Source
        .queue[(HttpRequest, Promise[HttpResponse])](bufferSize, overflowStrategy)
        .via(superPoolFlow)
        .to(Sink.foreach({
          case (Success(response), promise)  => sideEffect { promise.success(response) }
          case (Failure(exception), promise) => sideEffect { promise.failure(exception) }
        }))
        .run()

    private def requestResponse(request: HttpRequest): Future[HttpResponse] = {
      val responsePromise = Promise[HttpResponse]()
      queue.offer(request -> responsePromise).flatMap {
        case QueueOfferResult.Enqueued => responsePromise.future
        case QueueOfferResult.Dropped =>
          Future.failed(new RuntimeException("Queue overflowed. Try again later."))
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.QueueClosed =>
          Future
            .failed(
              new RuntimeException(
                "Queue was closed (pool shut down) while running the request. Try again later."))
      }
    }
  }
}
