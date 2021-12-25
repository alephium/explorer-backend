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

package org.alephium.explorer.benchmark

import scala.concurrent.ExecutionContext

/**
  * [[ExecutionContext]] that runs on current thread.
  */
object CurrentThreadExecutionContext extends ExecutionContext {

  implicit val self: ExecutionContext =
    this

  override def execute(runnable: Runnable): Unit =
    runnable.run()

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def reportFailure(cause: Throwable): Unit =
    throw new Exception("Failed to execute runnable", cause)

}
