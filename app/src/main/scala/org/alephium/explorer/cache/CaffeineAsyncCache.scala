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

import java.util.concurrent.CompletableFuture

import scala.concurrent.Future
import scala.jdk.FutureConverters._

import com.github.benmanes.caffeine.cache.AsyncLoadingCache

object CaffeineAsyncCache {

  @inline def apply[K, V](cache: AsyncLoadingCache[K, V]): CaffeineAsyncCache[K, V] =
    new CaffeineAsyncCache[K, V](cache)

}

/** A wrapper around [[AsyncLoadingCache]] to convert Java types to Scala. */
class CaffeineAsyncCache[K, V](cache: AsyncLoadingCache[K, V]) {

  def get(key: K): Future[V] =
    cache.get(key).asScala

  def getIfPresent(key: K): Option[Future[V]] = {
    val valueOrNull = cache.getIfPresent(key)
    if (valueOrNull == null) {
      None
    } else {
      Some(valueOrNull.asScala)
    }
  }

  def put(key: K, value: V): Unit =
    cache.put(key, CompletableFuture.completedFuture(value))

  def invalidate(key: K): Unit =
    cache.synchronous().invalidate(key)

  def invalidateAll(): Unit =
    cache.synchronous().invalidateAll()

}
