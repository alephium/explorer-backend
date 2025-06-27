// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.util

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging

object FutureUtil extends StrictLogging {

  implicit class FutureEnrichment[A](val future: Future[A]) extends AnyVal {

    /** Maps to function for the Future in this/current Thread
      *
      * @note
      *   DO NOT use to execute code that does not return immediately.
      * @see
      *   [[scala.concurrent.ExecutionContext.parasitic]] for details.
      */
    @inline def mapSync[B](f: A => B): Future[B] =
      future.map(f)(ExecutionContext.parasitic)

    /** Maps to the input value for the Future in this/current Thread
      *
      * @note
      *   DO NOT use to execute code that does not return immediately.
      * @see
      *   [[scala.concurrent.ExecutionContext.parasitic]] for details.
      */
    @inline def mapSyncToVal[B](value: B): Future[B] =
      future.map(_ => value)(ExecutionContext.parasitic)

    /** Maps to unit in this/current thread
      *
      * @note
      *   DO NOT use to execute code that does not return immediately.
      * @see
      *   [[scala.concurrent.ExecutionContext.parasitic]] for details.
      */
    @inline def mapSyncToUnit(): Future[Unit] =
      mapSyncToVal(())
  }
}
