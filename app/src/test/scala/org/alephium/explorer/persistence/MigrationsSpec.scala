// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.config.{ExplorerConfig, TestExplorerConfig}
import org.alephium.explorer.persistence.model.AppState.MigrationVersion
import org.alephium.util.{Duration, TimeStamp}

class MigrationsSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with TestDBRunner {

  private def configWithForkTimestamp(forkTimestamp: TimeStamp): ExplorerConfig = {
    val config = TestExplorerConfig()
    config.copy(
      consensus = config.consensus.copy(
        danube = config.consensus.danube.copy(forkTimestamp = forkTimestamp)
      )
    )
  }

  "migrationsQuery" should {
    "do nothing when the version is unknown" in {
      exec(Migrations.migrationsQuery(None)) is ()
    }

    "do nothing for the latest version" in {
      exec(Migrations.migrationsQuery(Some(MigrationVersion(Migrations.latestVersion.version)))) is
        ()
    }

    "reject future versions" in {
      intercept[Exception] {
        Migrations.migrationsQuery(
          Some(
            MigrationVersion(Migrations.latestVersion.version + 1)
          )
        )
      }.getMessage should include("Incompatible migration versions")
    }

    "apply all migrations from the initial version" in {
      exec(Migrations.migrationsQuery(Some(MigrationVersion(0)))) is ()
    }
  }

  "migration6" should {
    "skip groupless address migration before the fork" in {
      exec(
        Migrations.migration6(
          configWithForkTimestamp(TimeStamp.now().plusHoursUnsafe(24)),
          executionContext
        )
      ) is ()
    }

    "run groupless address migration after the fork" in {
      exec(
        Migrations.migration6(
          configWithForkTimestamp(TimeStamp.now().minusUnsafe(Duration.ofHoursUnsafe(24))),
          executionContext
        )
      ) is ()
    }
  }
}
