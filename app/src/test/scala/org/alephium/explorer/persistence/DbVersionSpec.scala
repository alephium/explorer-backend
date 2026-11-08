// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence

import slick.jdbc.PostgresProfile.api._
import slick.jdbc.meta.MTable

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.config.BootMode
import org.alephium.explorer.persistence.model.AppState.{DbVersion, MigrationVersion}
import org.alephium.explorer.persistence.queries.AppStateQueries
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.TokenHolderSchema

class DbVersionSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with TestDBRunner {
  "schema version" should {
    "initialize a fresh database and remain stable across restarts" in {
      Migrations.migrate(databaseConfig).futureValue
      exec(AppStateQueries.get(DbVersion)) is Some(Migrations.latestDbVersion)
      exec(AppStateQueries.get(MigrationVersion)) is Some(Migrations.latestVersion)
      Migrations.migrate(databaseConfig).futureValue
      exec(AppStateQueries.get(DbVersion)) is Some(Migrations.latestDbVersion)
    }

    "adopt a supported database that only has a migration version" in {
      exec(AppStateQueries.insertOrUpdate(Migrations.latestVersion))
      Migrations.migrate(databaseConfig).futureValue
      exec(AppStateQueries.get(DbVersion)) is Some(Migrations.latestDbVersion)
      exec(AppStateQueries.get(MigrationVersion)) is Some(Migrations.latestVersion)
    }

    "reject incompatible schema generations even when migration counters match" in {
      Seq(-1, 0, 2).foreach { version =>
        exec(AppStateQueries.insertOrUpdate(DbVersion(version)))
        exec(AppStateQueries.insertOrUpdate(Migrations.latestVersion))
        DBInitializer.initialize().failed.futureValue is a[IllegalStateException]
        exec(AppStateQueries.get(DbVersion)) is Some(DbVersion(version))
        exec(AppStateQueries.get(MigrationVersion)) is Some(Migrations.latestVersion)
      }
    }

    "reject a newer schema in read-only mode" in {
      exec(AppStateQueries.insertOrUpdate(DbVersion(2)))
      new org.alephium.explorer.persistence.Database(BootMode.ReadOnly)
        .startSelfOnce()
        .failed
        .futureValue is a[IllegalStateException]
      exec(AppStateQueries.get(DbVersion)) is Some(DbVersion(2))
    }

    "reject a newer schema before creating missing tables" in {
      exec(AppStateQueries.insertOrUpdate(DbVersion(2)))
      exec(TokenHolderSchema.table.schema.drop)
      try {
        DBInitializer.initialize().failed.futureValue is a[IllegalStateException]
        exec(MTable.getTables).exists(_.name.name == "token_holders") is false
        exec(AppStateQueries.get(DbVersion)) is Some(DbVersion(2))
      } finally {
        exec(TokenHolderSchema.table.schema.create)
      }
    }

    "not stamp a version when synchronous migration fails" in {
      exec(AppStateQueries.insertOrUpdate(MigrationVersion(-1)))
      Migrations.migrate(databaseConfig).failed.futureValue is a[Exception]
      exec(AppStateQueries.get(DbVersion)) is None
      exec(AppStateQueries.get(MigrationVersion)) is Some(MigrationVersion(-1))
    }
  }
}
