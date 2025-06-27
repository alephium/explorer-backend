// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.cache

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.config._
import org.alephium.explorer.persistence.Database

object TestTransactionCache {

  /** @return
    *   Test instance of [[TransactionCache]] for faster cache reloads than configured periods in
    *   `application.conf`
    */
  def apply()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): TransactionCache =
    TransactionCache(
      new Database(BootMode.ReadWrite)(ec, dc, TestExplorerConfig()),
      reloadAfter = 1.seconds
    )

}
