// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.util.concurrent.atomic.AtomicBoolean

import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.processors.PublishProcessor

final class CancelToken {
  private val cancelledRef = new AtomicBoolean(false)
  private val processor    = PublishProcessor.create[Unit]()

  def cancel(): Unit = {
    if (cancelledRef.compareAndSet(false, true)) {
      processor.onNext(())
      processor.onComplete()
    }
  }

  def isCancelled: Boolean = cancelledRef.get()

  def signal: Flowable[Unit] = processor
}
