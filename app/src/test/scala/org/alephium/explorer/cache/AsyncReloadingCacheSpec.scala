// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.cache

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future
import scala.concurrent.duration._

import org.alephium.explorer.AlephiumFutureSpec

class AsyncReloadingCacheSpec extends AlephiumFutureSpec {

  "reload cache after expiration while returning existing value even during reloading" in {
    val reloadCount = new AtomicInteger() // number of times reload was executed

    // a cache with initial value as 1 and reload set to 1.second
    val cache =
      AsyncReloadingCache(initial = 1, reloadAfter = 1.second) { currentValue =>
        Future {
          reloadCount.incrementAndGet() // reload invoked increment value
          currentValue + 1
        }
      }

    cache.get() is 1       // Returns initial cached value. There is time left to expiration
    reloadCount.get() is 0 // No reload yet

    eventually(cache.get() is 2) // eventually reload occurs with cache updated
    reloadCount.get() is 1       // first reload

    eventually(cache.get() is 3) // another reload & cache updated
    reloadCount.get() is 2       // second reload

    eventually(cache.get() is 4) // another reload & cache updated
    reloadCount.get() is 3       // third reload
  }

  "not allow multiple threads to concurrently execute reload" in {
    forAll { (cacheValue: Int) =>
      // 1 atomic and 1 non-atomic counter. If the cache is thread-safe then
      // the output of both counters will be same when the cache is concurrently
      // read and dropped by multiple threads
      val reloadAtomicCount    = new AtomicInteger(0)
      var reloadNonAtomicCount = 0

      // set reloadAfter to 1.millisecond so cache gets dropped and reloaded frequently
      val cache =
        AsyncReloadingCache(initial = cacheValue, reloadAfter = 1.millisecond) { currentValue =>
          Future {
            reloadAtomicCount.incrementAndGet()
            reloadNonAtomicCount += 1
            currentValue is cacheValue // cache value is always the same
            cacheValue
          }
        }

      // dispatch multiple concurrent cache reads.
      Future
        .sequence(List.fill(100000)(Future(cache.get())))
        .futureValue
        .distinct should contain only cacheValue

      // Because the cache is thread-safe both atomic and non-atomic counts should result in same output.
      reloadAtomicCount.get() is reloadNonAtomicCount
      // final cache value remains the same
      cache.get() is cacheValue
    }
  }

  "expireAndReload immediately" in {
    val reloadCount = new AtomicInteger() // number of times reload was executed

    // long 1.hour reload timeout to test manual expireAndReload
    val cache =
      AsyncReloadingCache(initial = 1, reloadAfter = 1.hour) { currentValue =>
        Future {
          reloadCount.incrementAndGet()
          currentValue + 1
        }
      }

    // dispatch multiple threads to concurrently execute cache.get() which triggers reload
    def concurrentlyReadCache(expectedCachedValue: Int) =
      Future.sequence(List.fill(100)(Future(cache.get()))).futureValue is
        List.fill(100)(expectedCachedValue)

    cache.expireAndReloadFuture().futureValue is true
    concurrentlyReadCache(2)
    reloadCount.get() is 1 // Initial value gets returned so not reload occur.

    cache.expireAndReloadFuture().futureValue is true
    concurrentlyReadCache(3)
    reloadCount.get() is 2

    cache.expireAndReloadFuture().futureValue is true
    concurrentlyReadCache(4)
    reloadCount.get() is 3
  }

  "reloadNow: should reload on boot" in {
    AsyncReloadingCache
      .reloadNow(reloadAfter = 1.hour)(Future(Int.MaxValue))
      .futureValue
      .get() is Int.MaxValue
  }
}
