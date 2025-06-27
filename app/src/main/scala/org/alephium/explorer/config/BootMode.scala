// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
