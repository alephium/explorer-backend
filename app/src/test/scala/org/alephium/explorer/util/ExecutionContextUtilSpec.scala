// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
