// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.util.{Duration, TimeStamp}

class WebSocketSyncServiceSpec extends AlephiumFutureSpec {
  "calculateBackoff" should {
    "calculate exponential backoff correctly" in {
      val baseDelay = Duration.ofSecondsUnsafe(1L)
      val maxDelay  = Duration.ofSecondsUnsafe(30L)

      WebSocketSyncService.calculateBackoff(0, baseDelay, maxDelay) is 1000L
      WebSocketSyncService.calculateBackoff(1, baseDelay, maxDelay) is 2000L
      WebSocketSyncService.calculateBackoff(2, baseDelay, maxDelay) is 4000L
      WebSocketSyncService.calculateBackoff(3, baseDelay, maxDelay) is 8000L
      WebSocketSyncService.calculateBackoff(4, baseDelay, maxDelay) is 16000L
      WebSocketSyncService.calculateBackoff(5, baseDelay, maxDelay) is 30000L
      WebSocketSyncService.calculateBackoff(6, baseDelay, maxDelay) is 30000L
      WebSocketSyncService.calculateBackoff(10, baseDelay, maxDelay) is maxDelay.millis
    }

    "respect max delay cap with large attempt numbers" in {
      val baseDelay = Duration.ofSecondsUnsafe(1L)
      val maxDelay  = Duration.ofSecondsUnsafe(10L)

      WebSocketSyncService.calculateBackoff(10, baseDelay, maxDelay) is maxDelay.millis
      WebSocketSyncService.calculateBackoff(50, baseDelay, maxDelay) is maxDelay.millis
    }

    "handle different base delays" in {
      val baseDelay = Duration.ofSecondsUnsafe(2L)
      val maxDelay  = Duration.ofSecondsUnsafe(60L)

      WebSocketSyncService.calculateBackoff(0, baseDelay, maxDelay) is 2000L
      WebSocketSyncService.calculateBackoff(1, baseDelay, maxDelay) is 4000L
      WebSocketSyncService.calculateBackoff(2, baseDelay, maxDelay) is 8000L
      WebSocketSyncService.calculateBackoff(3, baseDelay, maxDelay) is 16000L
    }
  }

  "nextReconnect" should {
    "return Right with incremented state when under max attempts" in {
      val initialState = WebSocketSyncService.ReconnectState(attempts = 0)
      val baseDelay    = Duration.ofSecondsUnsafe(1L)
      val maxDelay     = Duration.ofSecondsUnsafe(30L)

      val result = WebSocketSyncService.nextReconnect(
        initialState,
        maxAttempts = 5,
        baseDelay,
        maxDelay
      )

      result.isRight is true
      result.foreach { case (state, delay) =>
        state.attempts is 1
        delay is 2000L // 2^1 * 1000
      }
    }

    "return Left when max attempts reached" in {
      val maxedOut  = WebSocketSyncService.ReconnectState(attempts = 4)
      val baseDelay = Duration.ofSecondsUnsafe(1L)
      val maxDelay  = Duration.ofSecondsUnsafe(30L)

      val result = WebSocketSyncService.nextReconnect(
        maxedOut,
        maxAttempts = 5,
        baseDelay,
        maxDelay
      )

      result.isLeft is true
    }

    "increment attempts correctly through multiple calls" in {
      val baseDelay = Duration.ofSecondsUnsafe(1L)
      val maxDelay  = Duration.ofSecondsUnsafe(30L)
      val maxAttempts = 5

      var state = WebSocketSyncService.ReconnectState(attempts = 0)

      // First reconnect
      val result1 = WebSocketSyncService.nextReconnect(state, maxAttempts, baseDelay, maxDelay)
      result1.isRight is true
      result1.foreach { case (newState, delay) =>
        state = newState
        state.attempts is 1
        delay is 2000L
      }

      // Second reconnect
      val result2 = WebSocketSyncService.nextReconnect(state, maxAttempts, baseDelay, maxDelay)
      result2.isRight is true
      result2.foreach { case (newState, delay) =>
        state = newState
        state.attempts is 2
        delay is 4000L
      }

      // Third reconnect
      val result3 = WebSocketSyncService.nextReconnect(state, maxAttempts, baseDelay, maxDelay)
      result3.isRight is true
      result3.foreach { case (newState, delay) =>
        newState.attempts is 3
        delay is 8000L
      }
    }
  }

  "checkBlockFreshness" should {
    val maxBlockDelay             = Duration.ofSecondsUnsafe(30L)
    val maxConsecutiveStaleBlocks = 5
    val staleBlockWindowDuration  = Duration.ofSecondsUnsafe(10L)

    "return Continue decision for fresh message with zero consecutive old messages" in {
      val initialState = WebSocketSyncService.StaleBlockTracker(
        consecutiveStaleBlocks = 0,
        firstStaleBlockAt = None
      )
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(10L))

      val (newState, decision) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      newState.consecutiveStaleBlocks is 0
      newState.firstStaleBlockAt.isEmpty is true
      decision is WebSocketSyncService.StaleBlockDecision.Continue
    }

    "reset state when receiving fresh message after old messages" in {
      val initialState = WebSocketSyncService.StaleBlockTracker(
        consecutiveStaleBlocks = 3,
        firstStaleBlockAt = Some(TimeStamp.now().minusUnsafe(Duration.ofSecondsUnsafe(5L)))
      )
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(10L))

      val (newState, decision) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      newState.consecutiveStaleBlocks is 0
      newState.firstStaleBlockAt.isEmpty is true
      decision is WebSocketSyncService.StaleBlockDecision.Continue
    }

    "increment consecutive count for old message" in {
      val initialState = WebSocketSyncService.StaleBlockTracker(
        consecutiveStaleBlocks = 2,
        firstStaleBlockAt = Some(TimeStamp.now())
      )
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(60L))

      val (newState, decision) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      newState.consecutiveStaleBlocks is 3
      newState.firstStaleBlockAt.isDefined is true
      decision is WebSocketSyncService.StaleBlockDecision.Continue
    }

    "set first old message time on first old message" in {
      val initialState = WebSocketSyncService.StaleBlockTracker(
        consecutiveStaleBlocks = 0,
        firstStaleBlockAt = None
      )
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(60L))

      val (newState, decision) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      newState.consecutiveStaleBlocks is 1
      newState.firstStaleBlockAt.isDefined is true
      decision is WebSocketSyncService.StaleBlockDecision.Continue
    }

    "preserve first old message time across multiple old messages" in {
      val firstOldTime = TimeStamp.now().minusUnsafe(Duration.ofSecondsUnsafe(5L))
      val initialState = WebSocketSyncService.StaleBlockTracker(
        consecutiveStaleBlocks = 1,
        firstStaleBlockAt = Some(firstOldTime)
      )
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(60L))

      val (newState, _) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      newState.consecutiveStaleBlocks is 2
      newState.firstStaleBlockAt is Some(firstOldTime)
    }

    "trigger close when max consecutive old messages reached" in {
      val initialState = WebSocketSyncService.StaleBlockTracker(
        consecutiveStaleBlocks = 4,
        firstStaleBlockAt = Some(TimeStamp.now())
      )
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(60L))

      val (newState, decision) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      newState.consecutiveStaleBlocks is 5
      decision is WebSocketSyncService.StaleBlockDecision.CloseAndFallbackToHttp
    }

    "not trigger close when one below max consecutive" in {
      val initialState = WebSocketSyncService.StaleBlockTracker(
        consecutiveStaleBlocks = 3,
        firstStaleBlockAt = Some(TimeStamp.now())
      )
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(60L))

      val (newState, decision) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      newState.consecutiveStaleBlocks is 4
      decision is WebSocketSyncService.StaleBlockDecision.Continue
    }

    "trigger close when old message window duration exceeded" in {
      val firstOldTime = TimeStamp.now().minusUnsafe(Duration.ofSecondsUnsafe(15L))
      val initialState = WebSocketSyncService.StaleBlockTracker(
        consecutiveStaleBlocks = 2,
        firstStaleBlockAt = Some(firstOldTime)
      )
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(60L))

      val (_, decision) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      decision is WebSocketSyncService.StaleBlockDecision.CloseAndFallbackToHttp
    }

    "not trigger close when within window duration" in {
      val firstOldTime = TimeStamp.now().minusUnsafe(Duration.ofSecondsUnsafe(5L))
      val initialState = WebSocketSyncService.StaleBlockTracker(
        consecutiveStaleBlocks = 2,
        firstStaleBlockAt = Some(firstOldTime)
      )
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(60L))

      val (_, decision) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      decision is WebSocketSyncService.StaleBlockDecision.Continue
    }

    "trigger close exactly at window duration boundary" in {
      val firstOldTime = TimeStamp.now().minusUnsafe(Duration.ofSecondsUnsafe(10L))
      val initialState = WebSocketSyncService.StaleBlockTracker(
        consecutiveStaleBlocks = 2,
        firstStaleBlockAt = Some(firstOldTime)
      )
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(60L))

      val (_, decision) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      decision is WebSocketSyncService.StaleBlockDecision.CloseAndFallbackToHttp
    }

    "consider message old when exactly at delay boundary" in {
      val initialState = WebSocketSyncService.StaleBlockTracker()
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(30L))

      val (newState, decision) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      // At boundary, should be considered fresh (not greater than maxBlockDelay)
      newState.consecutiveStaleBlocks is 0
      decision is WebSocketSyncService.StaleBlockDecision.Continue
    }

    "consider message old when slightly over delay boundary" in {
      val initialState = WebSocketSyncService.StaleBlockTracker()
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(31L))

      val (newState, _) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      newState.consecutiveStaleBlocks is 1
    }

    "handle edge case with future timestamp (None difference)" in {
      val initialState = WebSocketSyncService.StaleBlockTracker()
      val now = TimeStamp.now()
      // Block timestamp in the future
      val blockTimestamp = now.plusUnsafe(Duration.ofSecondsUnsafe(1000L))

      val (newState, _) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      // Future timestamps should be considered old (getOrElse(true))
      newState.consecutiveStaleBlocks is 1
    }

    "handle multiple consecutive fresh messages" in {
      val firstState = WebSocketSyncService.StaleBlockTracker(consecutiveStaleBlocks = 3, firstStaleBlockAt = Some(TimeStamp.now()))
      val now = TimeStamp.now()

      // First fresh message resets
      val blockTimestamp1 = now.minusUnsafe(Duration.ofSecondsUnsafe(10L))
      val (newState1, decision1) = WebSocketSyncService.checkBlockFreshness(
        firstState,
        blockTimestamp1,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )
      newState1.consecutiveStaleBlocks is 0
      decision1 is WebSocketSyncService.StaleBlockDecision.Continue

      // Second fresh message maintains reset state
      val blockTimestamp2 = now.minusUnsafe(Duration.ofSecondsUnsafe(15L))
      val (newState2, decision2) = WebSocketSyncService.checkBlockFreshness(
        newState1,
        blockTimestamp2,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )
      newState2.consecutiveStaleBlocks is 0
      decision2 is WebSocketSyncService.StaleBlockDecision.Continue
    }

    "handle alternating old and fresh messages" in {
      val now = TimeStamp.now()

      // Old message
      val (state1, _) = WebSocketSyncService.checkBlockFreshness(
        WebSocketSyncService.StaleBlockTracker(),
        now.minusUnsafe(Duration.ofSecondsUnsafe(60L)),
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )
      state1.consecutiveStaleBlocks is 1

      // Fresh message resets
      val (state2, _) = WebSocketSyncService.checkBlockFreshness(
        state1,
        now.minusUnsafe(Duration.ofSecondsUnsafe(10L)),
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )
      state2.consecutiveStaleBlocks is 0

      // Old message again
      val (state3, _) = WebSocketSyncService.checkBlockFreshness(
        state2,
        now.minusUnsafe(Duration.ofSecondsUnsafe(60L)),
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )
      state3.consecutiveStaleBlocks is 1
    }

    "trigger close with both conditions met (consecutive AND duration)" in {
      val firstOldTime = TimeStamp.now().minusUnsafe(Duration.ofSecondsUnsafe(15L))
      val initialState = WebSocketSyncService.StaleBlockTracker(
        consecutiveStaleBlocks = 4,
        firstStaleBlockAt = Some(firstOldTime)
      )
      val now            = TimeStamp.now()
      val blockTimestamp = now.minusUnsafe(Duration.ofSecondsUnsafe(60L))

      val (newState, decision) = WebSocketSyncService.checkBlockFreshness(
        initialState,
        blockTimestamp,
        now,
        maxBlockDelay,
        maxConsecutiveStaleBlocks,
        staleBlockWindowDuration
      )

      newState.consecutiveStaleBlocks is 5
      decision is WebSocketSyncService.StaleBlockDecision.CloseAndFallbackToHttp
    }
  }
}
