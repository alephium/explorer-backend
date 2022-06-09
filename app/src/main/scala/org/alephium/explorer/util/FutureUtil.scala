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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.typesafe.scalalogging.StrictLogging

object FutureUtil extends StrictLogging {

  implicit class FutureEnrichment[A](val future: Future[A]) extends AnyVal {

    /**
      * Maps to function for the Future in this/current Thread
      *
      * @note DO NOT use to execute code that does not return immediately.
      *       @see [[scala.concurrent.ExecutionContext.parasitic]] for details.
      * */
    @inline def mapSync[B](f: A => B): Future[B] =
      future.map(f)(ExecutionContext.parasitic)

    /** Maps to the input value for the Future in this/current Thread
      *
      * @note DO NOT use to execute code that does not return immediately.
      *       @see [[scala.concurrent.ExecutionContext.parasitic]] for details.
      * */
    @inline def mapSyncToVal[B](value: B): Future[B] =
      future.map(_ => value)(ExecutionContext.parasitic)

    /** Maps to unit in this/current thread
      *
      * @note DO NOT use to execute code that does not return immediately.
      *       @see [[scala.concurrent.ExecutionContext.parasitic]] for details.
      * */
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
    *       Future(???) //If this Future fails, Scheduler is closed.
    *  }
    * }}}
    *
    * Multiple resources can be initialised with embedding. If the body of any
    * one of the resource fails, all parent resources are closed ensuring no leaks.
    * {{{
    *   managed(ActorSystem("")) { resource1 =>
    *     managed(Scheduler("")) { resource2 =>
    *       managed(someResource()) { resource3 =>
    *          Future(???) //If this Future fails, all resources above are closed
    *       }
    *     }
    *   }
    * }}}
    *
    * @param resource The resource to manage.
    * @param body     The body recover for failure.
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
