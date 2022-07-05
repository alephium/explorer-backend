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

import java.util.concurrent.{CompletableFuture, Executor, TimeUnit}

import com.github.benmanes.caffeine.cache.{AsyncCacheLoader, Caffeine}
import org.scalatest.concurrent.ScalaFutures

import org.alephium.explorer.AlephiumSpec

class CaffeineAsyncCacheSpec extends AlephiumSpec with ScalaFutures {

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

    //does not throw NullPointerException
    cache.getIfPresent(1) is None
    //insert value for key 1
    cache.put(1, "one")
    //get value
    cache.getIfPresent(1).map(_.futureValue) is Some("one")

  }

}
