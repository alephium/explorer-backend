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

package org.alephium.explorer.service

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.sideEffect
import org.alephium.util.Duration

trait SyncService extends StrictLogging {
  implicit def executionContext: ExecutionContext
  def syncPeriod: Duration
  def syncOnce(): Future[Unit]
  def init(): Future[Boolean] = Future.successful(true)

  private val stopped: Promise[Unit]  = Promise()
  private val syncDone: Promise[Unit] = Promise()
  private var startedOnce             = false
  private var initDone                = false

  def start(): Future[Unit] = {
    startedOnce = true
    Future.successful {
      sync()
    }
  }

  def stop(): Future[Unit] = {
    if (!startedOnce) {
      syncDone.failure(new IllegalStateException).future
    } else {
      sideEffect(if (!stopped.isCompleted) stopped.success(()))
      syncDone.future
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def sync(): Unit = {
    (if (!initDone) {
       init().flatMap { initialized =>
         if (initialized) {
           initDone = true
           syncOnce()
         } else {
           Future.successful(())
         }
       }
     } else {
       syncOnce()
     }).onComplete {
      case Success(_) =>
        continue()
      case Failure(e) =>
        logger.error("Failure while syncing", e)
        continue()
    }
    def continue() = {
      if (stopped.isCompleted) {
        syncDone.success(())
      } else {
        Thread.sleep(syncPeriod.millis)
        sync()
      }
    }
  }
}

object SyncService {
  trait BlockFlow extends SyncService {

    var nodeUris: Seq[Uri] = Seq.empty

    @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
    def start(newUris: Seq[Uri]): Future[Unit] = {
      nodeUris = newUris
      start()
    }
  }
}
