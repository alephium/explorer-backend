// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.cache

import java.util.concurrent.{CompletableFuture, Executor, TimeUnit}

import com.github.benmanes.caffeine.cache.{AsyncCacheLoader, Caffeine}

import org.alephium.explorer.AlephiumFutureSpec

class CaffeineAsyncCacheSpec extends AlephiumFutureSpec {

  "return None for getIfPresent when cache is empty (NullPointerException check)" in {
    val cache =
      CaffeineAsyncCache {
        Caffeine
          .newBuilder()
          .expireAfterWrite(10, TimeUnit.MINUTES)
          .maximumSize(10)
          .buildAsync[Int, String] {
            new AsyncCacheLoader[Int, String] {
              override def asyncLoad(key: Int, executor: Executor): CompletableFuture[String] =
                fail("Async load is not required for this test")
            }
          }
      }

    // does not throw NullPointerException
    cache.getIfPresent(1) is None
    // insert value for key 1
    cache.put(1, "one")
    // get value
    cache.getIfPresent(1).map(_.futureValue) is Some("one")

  }

}
