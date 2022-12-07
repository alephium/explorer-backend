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

import java.util.concurrent.Executor

import scala.concurrent.ExecutionContext

import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.error._

object ExecutionContextUtil extends StrictLogging {

  //Currently exiting on every error, we will fined grained this latter
  //if we see other errors happening that are non-fatal
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
