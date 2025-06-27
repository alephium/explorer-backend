// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.util

import java.util.concurrent.Executor

import scala.concurrent.ExecutionContext

import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.error._

object ExecutionContextUtil extends StrictLogging {

  // Currently exiting on every error, we will fined grained this latter
  // if we see other errors happening that are non-fatal
  def reporter(exit: => Unit): Throwable => Unit = {
    case fse: FatalSystemExit =>
      logger.error(s"Fatal error, closing app", fse)
      exit
    case other =>
      logger.error(s"Unexpected error, closing app", other)
      exit
  }

  def fromExecutor(executor: Executor, exit: => Unit): ExecutionContext =
    ExecutionContext.fromExecutor(executor, reporter(exit))
}
