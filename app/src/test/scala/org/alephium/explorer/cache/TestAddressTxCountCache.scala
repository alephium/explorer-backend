// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.cache

import scala.concurrent.ExecutionContext

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.config._
import org.alephium.explorer.persistence.Database

object TestAddressTxCountCache {

  /** @return
    *   Test instance of [[TransactionPerAddressCountCache]] for faster cache reloads than
    *   configured periods in `application.conf`
    */
  def apply()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): AddressTxCountCache =
    DBAddressTxCountCache(
      new Database(BootMode.ReadWrite)(ec, dc, TestExplorerConfig())
    )

}
