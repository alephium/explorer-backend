// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence

import scala.util._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.config.BootMode

/** Temporary placeholder. These tests should be merged into ApplicationSpec */
class DatabaseSpec extends AlephiumFutureSpec with DatabaseFixtureForEach {

  "initialiseDatabase" should {
    "successfully connect" when {
      "readOnly mode" in {
        val database: Database =
          new Database(BootMode.ReadOnly)

        Try(database.startSelfOnce().futureValue) is Success(())
      }

      "readWrite mode" in {
        val database: Database =
          new Database(BootMode.ReadWrite)

        Try(database.startSelfOnce().futureValue) is Success(())
      }
    }
  }
}
