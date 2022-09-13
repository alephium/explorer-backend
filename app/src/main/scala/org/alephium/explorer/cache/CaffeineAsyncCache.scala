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

import java.util.concurrent.{CompletableFuture, Executor}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import com.github.benmanes.caffeine.cache.{AsyncCacheLoader, AsyncLoadingCache, Caffeine}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.persistence.DBRunner
import org.alephium.util.AVector

object CaffeineAsyncCache {

  @inline def apply[K, V](cache: AsyncLoadingCache[K, V]): CaffeineAsyncCache[K, V] =
    new CaffeineAsyncCache[K, V](cache)

  /**
    * Caches the number of rows return by a query.
    *
    * @note Slick's [[slick.lifted.Query]] type does not implement equals or hashCode
    *       so this cache will not work as expected on dynamically generated queries.
    *       The queries to cache should be static i.e. should have the same memory
    *       address.
    *
    *       {{{
    *          //OK
    *          cacheRowCount.get(mainChainQuery)
    *          cacheRowCount.get(mainChainQuery)
    *
    *          //OK
    *          cacheRowCount.get(BlockHeaderSchema.table)
    *          cacheRowCount.get(BlockHeaderSchema.table)
    *
    *          //NOT OK
    *          cacheRowCount.get(BlockHeaderSchema.table.filter(_.mainChain))
    *          cacheRowCount.get(BlockHeaderSchema.table.filter(_.mainChain))
    *       }}}
    *
    * @param runner  Allows executing the input [[slick.lifted.Query]].
    * @param builder Pre-configured [[com.github.benmanes.caffeine.cache.Caffeine]]'s cache instance.
    */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def rowCountCache(runner: DBRunner)(
      builder: Caffeine[AnyRef, AnyRef]): CaffeineAsyncCache[Query[_, _, AVector], Int] =
    CaffeineAsyncCache {
      builder
        .asInstanceOf[Caffeine[Query[_, _, AVector], Int]]
        .buildAsync[Query[_, _, AVector], Int] {
          new AsyncCacheLoader[Query[_, _, AVector], Int] {
            override def asyncLoad(table: Query[_, _, AVector],
                                   executor: Executor): CompletableFuture[Int] =
              runner.run(table.length.result).asJava.toCompletableFuture
          }
        }
    }
}

/** A wrapper around [[com.github.benmanes.caffeine.cache.AsyncLoadingCache]] to convert Java types to Scala. */
class CaffeineAsyncCache[K, V](cache: AsyncLoadingCache[K, V]) {

  def get(key: K): Future[V] =
    cache.get(key).asScala

  //TODO add `asJava` to `AVector`
  def getAll(keys: AVector[K])(implicit ec: ExecutionContext): Future[AVector[(K, V)]] =
    cache.getAll(keys.toSeq.asJava).asScala.map(mp => AVector.unsafe(mp.asScala.toArray))

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
