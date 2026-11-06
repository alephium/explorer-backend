// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.cache

import java.util.concurrent.{CompletableFuture, Executor, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*

import com.github.benmanes.caffeine.cache.{AsyncCacheLoader, Caffeine}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.persistence.{DBAction, DBRunner}
import org.alephium.explorer.persistence.schema.BlockHeaderSchema

class CaffeineAsyncCacheSpec extends AlephiumFutureSpec {

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private def unsafeCast[R](value: Int): R =
    value.asInstanceOf[R]

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private def rowCountQuery(query: Query[_, _, Seq]): Query[_, _, ArraySeq] =
    query.asInstanceOf[Query[_, _, ArraySeq]]

  private def newCache(
      loaderCount: AtomicInteger
  ): CaffeineAsyncCache[Int, String] =
    CaffeineAsyncCache {
      Caffeine
        .newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .maximumSize(10)
        .buildAsync[Int, String] {
          new AsyncCacheLoader[Int, String] {
            override def asyncLoad(key: Int, executor: Executor): CompletableFuture[String] = {
              loaderCount.incrementAndGet()
              CompletableFuture.completedFuture(s"value-$key")
            }

            override def asyncLoadAll(
                keys: java.util.Set[_ <: Int],
                _executor: Executor
            ): CompletableFuture[java.util.Map[_ <: Int, _ <: String]] = {
              val result = new java.util.HashMap[Int, String]()
              keys.iterator().asScala.foreach { key =>
                loaderCount.incrementAndGet()
                result.put(key, s"value-$key")
              }
              CompletableFuture.completedFuture(result)
            }
          }
        }
    }

  "return None for getIfPresent when cache is empty (NullPointerException check)" in {
    val cache = newCache(new AtomicInteger(0))

    // does not throw NullPointerException
    cache.getIfPresent(1) is None
    // insert value for key 1
    cache.put(1, "one")
    // get value
    cache.getIfPresent(1).map(_.futureValue) is Some("one")

  }

  "load values with get and getAll" in {
    val loaderCount = new AtomicInteger(0)
    val cache       = newCache(loaderCount)

    cache.get(1).futureValue is "value-1"
    loaderCount.get() is 1

    cache.getAll(List(1, 2)).futureValue.toSet is Set(1 -> "value-1", 2 -> "value-2")
    loaderCount.get() is 2
  }

  "invalidate cached values" in {
    val cache = newCache(new AtomicInteger(0))

    cache.put(1, "one")
    cache.put(2, "two")

    cache.invalidate(1)
    cache.getIfPresent(1) is None
    cache.getIfPresent(2).map(_.futureValue) is Some("two")

    cache.invalidateAll()
    cache.getIfPresent(1) is None
    cache.getIfPresent(2) is None
  }

  "load row counts with rowCountCache" in {
    val calls = new AtomicInteger(0)
    val runner = new DBRunner {
      override def databaseConfig: slick.basic.DatabaseConfig[slick.jdbc.PostgresProfile] =
        throw new UnsupportedOperationException("unused")

      override def run[R, E <: Effect](action: DBAction[R, E]): Future[R] = {
        calls.incrementAndGet()
        Future.successful(unsafeCast[R](42))
      }
    }

    val cache =
      CaffeineAsyncCache.rowCountCache(runner)(Caffeine.newBuilder().maximumSize(10))

    cache.get(rowCountQuery(BlockHeaderSchema.table)).futureValue is 42
    calls.get() is 1
  }

}
