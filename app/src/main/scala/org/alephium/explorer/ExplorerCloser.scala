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

package org.alephium.explorer

import scala.concurrent.{ExecutionContext, Future}

/** Background services like sync requires system shutdown when they encounter an invalid state.
  * This trait exposes only the `close` function from [[org.alephium.explorer.ExplorerState]].
  *
  **/
trait ExplorerCloser {

  /** Closes the explorer */
  def close()(implicit ec: ExecutionContext): Future[Unit]

}
