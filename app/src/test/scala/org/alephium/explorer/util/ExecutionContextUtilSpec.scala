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

package org.alephium.explorer.util

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext

import org.scalatest.concurrent.Eventually

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.error.ExplorerError._

@SuppressWarnings(Array("org.wartremover.warts.ThreadSleep"))
class ExecutionContextSpec extends AlephiumSpec with Eventually {

  "ExecutionContextUtil" should {
    val count = new AtomicInteger(0)

    def exit() = {
      count.incrementAndGet()
      ()
    }

    val executionContext = ExecutionContextUtil.fromExecutor(ExecutionContext.global, exit())

    "exit on a fatal explorer error" in {
      executionContext.reportFailure(new NodeError(new Throwable("error")))
      eventually(count.get() is 1)
    }

    "exit on any error" in {
      executionContext.reportFailure(new Throwable("error"))
      eventually(count.get() is 2)
    }
  }
}
