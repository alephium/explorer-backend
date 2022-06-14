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

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.AkkaHttpServer
import org.alephium.explorer.util.FutureUtil._

/** Type-classes for things that can be terminated asynchronously */
@implicitNotFound("Type class implementation not found for AsyncCloseable of type ${A}")
trait AsyncCloseable[A] {

  def close(closeable: A)(implicit ec: ExecutionContext): Future[Unit]

}

object AsyncCloseable extends StrictLogging {

  /** [[AsyncCloseable]] for Postgres */
  implicit object PostgresAsyncCloseable extends AsyncCloseable[DatabaseConfig[PostgresProfile]] {
    override def close(dc: DatabaseConfig[PostgresProfile])(
        implicit ec: ExecutionContext): Future[Unit] =
      Future.fromTry(Try(dc.db.close()))
  }

  /** [[AsyncCloseable]] for Scheduler */
  implicit object SchedulerAsyncCloseable extends AsyncCloseable[Scheduler] {
    override def close(scheduler: Scheduler)(implicit ec: ExecutionContext): Future[Unit] =
      Future.fromTry(Try(scheduler.close())) //This will be made async in issue #252
  }

  implicit object OptionSchedulerAsyncCloseable extends AsyncCloseable[Option[Scheduler]] {
    override def close(closeable: Option[Scheduler])(implicit ec: ExecutionContext): Future[Unit] =
      closeable match {
        case Some(value) => SchedulerAsyncCloseable.close(value)
        case None        => Future.unit
      }
  }

  /** [[AsyncCloseable]] for Akka HTTP server binding */
  implicit object HttpServerBindingAsyncCloseable extends AsyncCloseable[Http.ServerBinding] {
    override def close(closeable: Http.ServerBinding)(implicit ec: ExecutionContext): Future[Unit] =
      closeable.unbind().mapSyncToUnit()
  }

  /** [[AsyncCloseable]] for Akka HTTP server binding */
  implicit object AkkaHttpServerAsyncCloseable extends AsyncCloseable[AkkaHttpServer] {
    override def close(closeable: AkkaHttpServer)(implicit ec: ExecutionContext): Future[Unit] =
      closeable.close()
  }

  /** [[AsyncCloseable]] for Akka ActorSystem */
  implicit object ActorSystemAsyncCloseable extends AsyncCloseable[ActorSystem] {
    override def close(closeable: ActorSystem)(implicit ec: ExecutionContext): Future[Unit] =
      closeable.terminate().mapSyncToUnit()
  }

  /** Provides [[AsyncCloseable]] for all [[java.lang.AutoCloseable]] types */
  implicit def fromAutoCloseable[A <: AutoCloseable]: AsyncCloseable[A] =
    new AsyncCloseable[A] {
      override def close(closeable: A)(implicit ec: ExecutionContext): Future[Unit] =
        Future.fromTry(Try(closeable.close()))
    }

  /** Executes a closeable given it's type-class */
  @inline def close[A](resource: A)(implicit closeable: AsyncCloseable[A],
                                    ec: ExecutionContext): Future[Unit] =
    closeable.close(resource)

  /**
    * Convenience/Sugar implicits to provide `close` for all [[AsyncCloseable]] types
    *
    * Eg: {{{
    *   import AsyncCloseable._
    *   ActorSystem("Akka").close()
    * }}}
    *
    * */
  implicit class AsyncCloseableImplicit[A](val resource: A) extends AnyVal {
    def close()(implicit ec: ExecutionContext, closeable: AsyncCloseable[A]): Future[Unit] =
      AsyncCloseable.close(resource)
  }

}
