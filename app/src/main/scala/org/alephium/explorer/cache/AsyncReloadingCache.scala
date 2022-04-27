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

package org.alephium.explorer.cache

import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.util.{Failure, Success}

import com.typesafe.scalalogging.LazyLogging

object AsyncReloadingCache {

  /**
    * Example:
    * {{{
    *    val cache =
    *      AsyncReloadingCache(initial = "Initial value", reloadAfter = 1.second) {
    *        currentValue: String =>
    *          Future {
    *            currentValue + " updated"
    *          }
    *       }
    *
    *     val value: String = cache.get()
    * }}}
    */
  @inline def apply[T](initial: T, reloadAfter: FiniteDuration)(
      loader: T => Future[T]): AsyncReloadingCache[T] =
    new AsyncReloadingCache[T](
      value       = initial,
      deadline    = reloadAfter.fromNow,
      reloading   = new AtomicBoolean(),
      reloadAfter = reloadAfter,
      loader      = loader
    )

  /** Expires and reloads the cache on boot */
  @inline def expireAndReload[T](initial: T, reloadAfter: FiniteDuration)(
      loader: T => Future[T]): AsyncReloadingCache[T] = {
    val cache =
      AsyncReloadingCache(
        initial     = initial,
        reloadAfter = reloadAfter
      )(loader)

    cache.expireAndReload()

    cache
  }
}

/**
  * Cache that reloads value asynchronously returning the existing
  * cached value while reload/re-cache is in the progress in the background.
  *
  * @param value       Current cached value
  * @param deadline    Deadline until expiration
  * @param reloading   Allows only a single thread to execute reloading
  * @param reloadAfter How often to reload the cache or how long to keep existing cache value alive.
  * @param loader      A function given the existing cached value returns the next value to cache.
  * @tparam T Cached value's type
  */
class AsyncReloadingCache[T](@volatile private var value: T,
                             @volatile private var deadline: Deadline,
                             reloading: AtomicBoolean,
                             reloadAfter: FiniteDuration,
                             loader: T => Future[T])
    extends LazyLogging {

  /** Returns the latest cached value and invokes reload */
  def get(): T = {
    reload()
    value
  }

  /**
    * Instantly expires the current cached value and invokes reload.
    *
    * @note This does not always guarantee instant reload will occur immediately in this
    *       call for cases when another thread is currently successfully executing reload.
    * */
  def expireAndReload(): Unit = {
    expire()
    reload()
  }

  /** Sets the current cached value as expired which gets reload on next [[get]] */
  def expire(): Unit =
    deadline = Deadline.now

  private def reload(): Unit =
    //Reload if expired and not already being reloaded by another thread
    if (deadline.isOverdue() && reloading.compareAndSet(false, true)) {
      //fetch and set next cache state while staying in the current
      //ExecutionContext since the state update is inexpensive.
      loader(value).onComplete { result =>
        result match {
          case Success(value) =>
            this.value = value

          case Failure(exception) =>
            //Fatal error: Log for debugging.
            logger.error("Failed to reload cache", exception)
        }

        //set next deadline
        deadline = reloadAfter.fromNow
        //release to allow next reload
        reloading.set(false)
      }(ExecutionContext.parasitic)
    }
}
