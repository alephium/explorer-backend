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

package org.alephium.explorer.config

import scala.util.{Failure, Success, Try}

import org.alephium.explorer.error.ExplorerError.InvalidBootMode

/** Configures Explorer's boot-up */
sealed trait BootMode {
  def productPrefix: String
}

object BootMode {

  /** [[BootMode]]s with reads enabled */
  sealed trait Readable extends BootMode
  sealed trait Writable extends BootMode

  /** Serve HTTP requests only. Disables Sync. */
  case object ReadOnly extends Readable

  /** Enables both Read and Sync */
  case object ReadWrite extends Readable with Writable

  /** Enables Sync only */
  case object WriteOnly extends Writable

  /** All boot modes */
  def all: Array[BootMode] =
    Array(ReadOnly, ReadWrite, WriteOnly)

  /** @return [[BootMode]] that matches [[BootMode.productPrefix]] */
  def apply(mode: String): Option[BootMode] =
    all.find(_.productPrefix == mode)

  /** @return [[BootMode]] if found else fails */
  def validate(mode: String): Try[BootMode] =
    BootMode(mode) match {
      case Some(mode) => Success(mode)
      case None       => Failure(InvalidBootMode(mode))
    }

  def writable(mode: BootMode): Boolean =
    mode match {
      case _: Writable => true
      case _           => false
    }
  def readable(mode: BootMode): Boolean =
    mode match {
      case _: Readable => true
      case _           => false
    }
}
