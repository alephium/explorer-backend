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

  /**
    * Used when initial value is unknown during compile-time and
    * should be fetched asynchronously during runtime via `loader`.
    *
    * {{{
    *   val cache =
    *     AsyncReloadingCache.reloadNow(reloadAfter = 5.seconds) {
    *       Future("my cached values")
    *     }
    * }}}
    */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def reloadNow[T](reloadAfter: FiniteDuration)(loader: => Future[T])(
      implicit ec: ExecutionContext): Future[AsyncReloadingCache[T]] = {
    //scalastyle:off null
    //Null is internal only. Initial value is never shared with the client
    //so null is never accessed and NullPointerException will never occur.
    val cache =
      AsyncReloadingCache[T](
        initial     = null.asInstanceOf[T],
        reloadAfter = reloadAfter
      )(_ => loader)
    //scalastyle:on null

    //Reload the cache. If reload was not executed (unlikely to happen) fail the future.
    cache.expireAndReloadFuture() flatMap { reloaded =>
      if (reloaded) {
        Future.successful(cache)
      } else {
        Future.failed(new Exception("Failed to load cache on boot-up."))
      }
    }
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

  def expireAndReloadFuture()(implicit ec: ExecutionContext): Future[Boolean] = {
    expire()
    reloadFuture()
  }

  /** Sets the current cached value as expired which gets reload on next [[get]] */
  def expire(): Unit =
    deadline = Deadline.now

  /** Reload and update state in the current [[ExecutionContext]].
    * State update is inexpensive therefore local [[ExecutionContext]] is used.
    * */
  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  private def reload(): Unit =
    reloadFuture()(ExecutionContext.parasitic): Unit

  /** Reload the cache and updates state only in the given [[ExecutionContext]] */
  private def reloadFuture()(implicit ec: ExecutionContext): Future[Boolean] =
    //Reload if expired and not already being reloaded by another thread
    if (deadline.isOverdue() && reloading.compareAndSet(false, true)) {
      val cacheFuture =
        loader(value)
          .map { newValue =>
            //Set the next value immediately after the future is complete
            this.value = newValue
            true
          }
          .recoverWith { error =>
            //Log the error and return the same error
            logger.error("Failed to reload cache", error)
            Future.failed(error)
          }

      //Release cache for next cache update run only after
      // - current newCacheValue is set
      // - OR if the Future fails
      cacheFuture onComplete { _ =>
        //set next deadline
        deadline = reloadAfter.fromNow
        //release to allow next reload
        reloading.set(false)
      }

      cacheFuture

    } else {
      Future.successful(false)
    }
}
