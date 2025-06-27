// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

@scala.annotation.nowarn
final case class Pagination private (page: Int, limit: Int) {
  val offset: Int = (page - 1) * limit
}

object Pagination {

  @scala.annotation.nowarn
  final case class Reversible private (page: Int, limit: Int, reverse: Boolean) {
    val offset: Int = (page - 1) * limit
  }
  object Reversible {
    @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
    def unsafe(page: Int, limit: Int, reverse: Boolean = false): Reversible = {
      Reversible(page, limit, reverse)
    }
  }
  val defaultPage: Int        = 1
  val defaultLimit: Int       = 20
  val futureDefaultLimit: Int = 10
  val maxLimit: Int           = 100
  val futureMaxLimit: Int     = 20

  def default: Pagination = Pagination(defaultPage, defaultLimit)

  def unsafe(page: Int, limit: Int): Pagination = {
    Pagination(page, limit)
  }
}
