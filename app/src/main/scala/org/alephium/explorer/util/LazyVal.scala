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

import com.typesafe.scalalogging.StrictLogging

object LazyVal {

  @inline def apply[A](f: => A): LazyVal[A] =
    new LazyVal[A](
      fetch = () => f,
      value = None
    )

}

/**
  * Behaves exactly the same as Scala's `lazy` keyword difference being
  *  - This initialises the value only when [[get]] is invoked.
  *  - Maintains a state to check if the value is initialised or not.
  *
  * @param fetch Functions that returns the value to initialise lazily.
  * @param value The state of the lazy value
  */
final class LazyVal[A] private (fetch: () => A, @volatile private var value: Option[A])
    extends StrictLogging {

  def get(): A =
    this.value match {
      case Some(value) =>
        value

      case None =>
        fetchAndSet()
    }

  def foreach(f: A => Unit): Unit =
    value.foreach(f)

  def clear(): Unit =
    value = None

  def isDefined: Boolean =
    value.isDefined

  def isEmpty: Boolean =
    value.isEmpty

  private def fetchAndSet(): A =
    synchronized {
      try {
        value getOrElse {
          val value = fetch()
          this.value = Some(value)
          value
        }
      } catch {
        case err: Throwable =>
          logger.error(s"Failed to get ${this.getClass.getSimpleName}", err)
          throw err
      }
    }
}
