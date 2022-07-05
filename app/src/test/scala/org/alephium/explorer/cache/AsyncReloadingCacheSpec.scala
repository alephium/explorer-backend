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

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import org.alephium.explorer.AlephiumSpec

class AsyncReloadingCacheSpec extends AlephiumSpec with ScalaFutures with Eventually {

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  "reload cache after expiration while returning existing value even during reloading" in {
    val reloadCount = new AtomicInteger() //number of times reload was executed

    //a cache with initial value as 1 and reload set to 1.second
    val cache =
      AsyncReloadingCache(initial = 1, reloadAfter = 1.second) { currentValue =>
        Future {
          reloadCount.incrementAndGet() //reload invoked increment value
          currentValue + 1
        }
      }

    cache.get() is 1 //Returns initial cached value. There is time left to expiration
    reloadCount.get() is 0 //No reload yet

    eventually(Timeout(1.5.seconds))(cache.get() is 2) //eventually reload occurs with cache updated
    reloadCount.get() is 1 //first reload

    eventually(Timeout(1.5.seconds))(cache.get() is 3) //another reload & cache updated
    reloadCount.get() is 2 //second reload

    eventually(Timeout(1.5.seconds))(cache.get() is 4) //another reload & cache updated
    reloadCount.get() is 3 //third reload
  }

  "not allow multiple threads to concurrently execute reload" in {
    val reloadCount = new AtomicInteger() //number of times reload was executed

    //a cache with initial value as 1 and reload set to 2.seconds
    val cache =
      AsyncReloadingCache(initial = 1, reloadAfter = 2.seconds) { currentValue =>
        Future {
          reloadCount.incrementAndGet()
          currentValue + 1
        }
      }

    //dispatch multiple threads to concurrently execute cache.get() which triggers reload
    def concurrentlyReadCache(expectedCachedValue: Int) =
      Future.sequence(List.fill(100)(Future(cache.get()))).futureValue is
        List.fill(100)(expectedCachedValue)

    concurrentlyReadCache(1)
    reloadCount.get() is 0 //Initial value gets returned so not reload occur.

    eventually(Timeout(3.seconds)) {
      concurrentlyReadCache(2)
      reloadCount.get() is 1
    }
  }

  "expireAndReload immediately" in {
    val reloadCount = new AtomicInteger() //number of times reload was executed

    //long 1.hour reload timeout to test manual expireAndReload
    val cache =
      AsyncReloadingCache(initial = 1, reloadAfter = 1.hour) { currentValue =>
        Future {
          reloadCount.incrementAndGet()
          currentValue + 1
        }
      }

    //dispatch multiple threads to concurrently execute cache.get() which triggers reload
    def concurrentlyReadCache(expectedCachedValue: Int) =
      Future.sequence(List.fill(100)(Future(cache.get()))).futureValue is
        List.fill(100)(expectedCachedValue)

    cache.expireAndReload()
    concurrentlyReadCache(2)
    reloadCount.get() is 1 //Initial value gets returned so not reload occur.

    cache.expireAndReload()
    eventually(Timeout(1.second)) {
      concurrentlyReadCache(3)
      reloadCount.get() is 2
    }

    cache.expireAndReload()
    eventually(Timeout(1.second)) {
      concurrentlyReadCache(4)
      reloadCount.get() is 3
    }
  }

  "reloadNow: should reload on boot" in {
    AsyncReloadingCache
      .reloadNow(reloadAfter = 1.hour)(Future(Int.MaxValue))
      .futureValue
      .get() is Int.MaxValue
  }
}
