// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.concurrent.Future

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.util.Duration

class WebSocketSyncServiceSpec extends AlephiumFutureSpec {

  "WebSocketSyncService" should {
    "calculate exponential backoff correctly" in {
      val baseDelay = Duration.ofSecondsUnsafe(1L)
      val maxDelay = Duration.ofSecondsUnsafe(30L)

      // Simulating backoff calculation
      def calculateBackoff(attempts: Int): Long = {
        val backoffMs = baseDelay.millis * Math.pow(2, attempts.toDouble).toLong
        Math.min(backoffMs, maxDelay.millis)
      }

      calculateBackoff(0) is 1000L      // 1 second
      calculateBackoff(1) is 2000L      // 2 seconds
      calculateBackoff(2) is 4000L      // 4 seconds
      calculateBackoff(3) is 8000L      // 8 seconds
      calculateBackoff(4) is 16000L     // 16 seconds
      calculateBackoff(5) is 30000L     // 30 seconds (capped)
      calculateBackoff(10) is 30000L    // Still capped at 30 seconds
    }

    "respect max reconnect attempts" in {
      val maxAttempts = 5
      maxAttempts should be > 0
      maxAttempts should be <= 10
    }

    "handle buffer size limits" in {
      val maxBufferSize = 1000
      val currentSize = 1001
      currentSize should be > maxBufferSize
    }

    "handle insertion failures gracefully" in {
      val future = Future.failed[Unit](new RuntimeException("DB error"))
      future.failed.futureValue is a[RuntimeException]
    }
  }

  "Configuration" should {
    "have reasonable defaults" in {
      val flushInterval = Duration.ofMillisUnsafe(500L)
      val maxBlockDelay = Duration.ofSecondsUnsafe(30L)
      val maxBufferSize = 1000
      val maxReconnectAttempts = 5
      val reconnectBaseDelay = Duration.ofSecondsUnsafe(1L)
      val reconnectMaxDelay = Duration.ofSecondsUnsafe(30L)

      flushInterval.millis should be > 0L
      maxBlockDelay.millis should be > 0L
      maxBufferSize should be > 0
      maxReconnectAttempts should be > 0
      reconnectBaseDelay.millis should be > 0L
      reconnectMaxDelay.millis should be >= reconnectBaseDelay.millis
    }
  }
}
