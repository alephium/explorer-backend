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

package org.alephium.explorer.util

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import com.typesafe.scalalogging.StrictLogging

object FutureUtil extends StrictLogging {

  implicit class FutureEnrichment[A](val future: Future[A]) extends AnyVal {

    //Convenience function to wait on a future
    @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
    @inline def await(seconds: FiniteDuration = 5.seconds): A =
      Await.result(future, seconds)

    /** Maps to function for the Future in this/current Thread */
    @inline def mapSync[B](f: A => B): Future[B] =
      future.map(f)(ExecutionContext.parasitic)

    /** Maps to the input value for the Future in this/current Thread */
    @inline def mapSyncToVal[B](value: B): Future[B] =
      future.map(_ => value)(ExecutionContext.parasitic)

    @inline def mapSyncToUnit(): Future[Unit] =
      mapSyncToVal(())
  }

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def managed[R: AsyncCloseable, T](resource: Future[R])(body: R => Future[T])(
      implicit ec: ExecutionContext): Future[T] =
    resource flatMap { resource =>
      managed(resource)(body)
    }

  /**
    * Ensures that the `resource` is always closed in-case the `body` fails.
    * {{{
    *  managed(Scheduler("")) {
    *     scheduler =>
    *       Future(???) //Do something with the scheduler
    *  }
    * }}}
    *
    * Multiple resources can also be initialised. If the body of any one of the resource,
    * parent resources are all closed ensuring no leaks.
    * {{{
    *   managed(ActorSystem("")) { resource1 =>
    *     managed(Scheduler("")) { resource2 =>
    *       managed(someResource()) { resource3 =>
    *          Future("Do something!")
    *       }
    *     }
    *   }
    * }}}
    *
    * @param resource The resource to manage
    * @param body     The body checked for failure.
    *
    * @return The result of the body.
    */
  def managed[R: AsyncCloseable, T](resource: => R)(body: R => Future[T])(
      implicit ec: ExecutionContext): Future[T] =
    Future.fromTry(Try(resource)) flatMap { resource =>
      body(resource) recoverWith {
        case exception =>
          AsyncCloseable
            .close(resource)
            .onComplete {
              case Failure(throwable) =>
                logger.error(s"Failed to stop resource ${resource.getClass.getName}", throwable)
                Future.failed(throwable)

              case Success(_) =>
                logger.trace(s"Resource ${resource.getClass.getName} stopped.")
            }

          Future.failed(exception)
      }
    }

}
