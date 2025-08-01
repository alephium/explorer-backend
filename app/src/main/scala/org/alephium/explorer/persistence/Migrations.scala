// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence.model.AppState.MigrationVersion
import org.alephium.explorer.persistence.queries.AppStateQueries
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.{Address, BlockHash}
import org.alephium.protocol.vm.LockupScript
import org.alephium.util.TimeStamp

@SuppressWarnings(Array("org.wartremover.warts.AnyVal"))
object Migrations extends StrictLogging {

  val latestVersion: MigrationVersion = MigrationVersion(9)

  def migration1(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    // We retrigger the download of fungible and non-fungible tokens' metadata that have sub-category
    for {
      _ <-
        sqlu"""UPDATE token_info SET interface_id = NULL WHERE category = '0001' AND interface_id != '0001'"""
      _ <-
        sqlu"""UPDATE token_info SET interface_id = NULL WHERE category = '0003' AND interface_id != '0003'"""
    } yield ()
  }

  def migration2(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    // Finalize missing outputs and token_outputs
    // Due to finalization concurrency issue.
    for {
      _ <-
        sqlu"""
          UPDATE outputs o
          SET spent_finalized = i.tx_hash, spent_timestamp = i.block_timestamp
          FROM inputs i
          WHERE i.output_ref_key = o.key
          AND o.main_chain=true
          AND i.main_chain=true
          AND o.spent_finalized IS NULL
        """
      _ <-
        sqlu"""
          UPDATE token_outputs o
          SET spent_finalized = i.tx_hash, spent_timestamp = i.block_timestamp
          FROM inputs i
          WHERE i.output_ref_key = o.key
          AND o.main_chain=true
          AND i.main_chain=true
          AND o.spent_finalized IS NULL
        """
    } yield ()
  }

  def migration3(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    // Reset token_supply table as some supply was wrong due to the finalization concurrency issue
    // Token supply will be re-computed from scratch, this will take some time until the newest
    // latest supply is computed.
    for {
      _ <-
        sqlu"""
          TRUNCATE token_supply
        """
    } yield ()
  }
  /*
   * Empty transaction due to the coinbase migration being disabled.
   */
  def migration4: DBActionAll[Unit] = DBIOAction.successful(())

  private def addGrouplessAddressColumn(
      tableName: String,
      grouplessAddressColumn: String
  ): DBActionAll[Int] =
    sqlu"""ALTER TABLE #$tableName ADD COLUMN IF NOT EXISTS #$grouplessAddressColumn CHARACTER VARYING"""

  def migration5(implicit ec: ExecutionContext): DBActionAll[Unit] =
    for {
      _ <- addGrouplessAddressColumn("outputs", "groupless_address")
      _ <- addGrouplessAddressColumn("token_outputs", "groupless_address")
      _ <- addGrouplessAddressColumn("token_tx_per_addresses", "groupless_address")
      _ <- addGrouplessAddressColumn("transaction_per_addresses", "groupless_address")
      _ <- addGrouplessAddressColumn("inputs", "output_ref_groupless_address")
    } yield ()

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def updateGrouplessAddressColumn(
      tableName: String,
      addressColumn: String,
      grouplessAddressColumn: String,
      chain: String = "chain_to"
  )(implicit
      explorerConfig: ExplorerConfig,
      ec: ExecutionContext
  ): DBActionAll[Unit] = {
    logger.info(s"Migrating $tableName")
    for {
      addresses <- sql"""
          SELECT #$addressColumn, block_hash
          FROM #$tableName
          WHERE block_timestamp >= ${explorerConfig.consensus.danube.forkTimestamp}
          AND #$grouplessAddressColumn IS NULL
        """
        .asAS[(Address, BlockHash)]
        .map { addresses =>
          addresses.filter {
            case (Address.Asset(_: LockupScript.P2PK), _) =>
              true
            case (Address.Asset(_: LockupScript.P2HMPK), _) =>
              true
            case _ =>
              false
          }
        }
      _ <- DBIOAction.sequence(addresses.map { case (address, blockHash) =>
        sqlu"""
          UPDATE #$tableName
          SET #$grouplessAddressColumn = bh.#$chain
          FROM block_headers bh
          WHERE #$tableName.#$addressColumn = $address
            AND #$tableName.block_hash = bh.hash
            AND bh.hash = $blockHash;
        """
      })
    } yield ()
  }

  def migration6(implicit explorerConfig: ExplorerConfig, ec: ExecutionContext): DBActionAll[Unit] =
    if (TimeStamp.now().isBefore(explorerConfig.consensus.danube.forkTimestamp)) {
      // No need to migrate the groupless_address as no groupless addresses can be created before the Danube Hard Fork.
      DBIOAction.successful(())
    } else {
      logger.info(
        "Migrating `addresse_like`, might be slow if you have a lot of groupless addresses"
      )
      for {
        _ <- updateGrouplessAddressColumn("outputs", "address", "groupless_address")
        _ <- updateGrouplessAddressColumn("token_outputs", "address", "groupless_address")
        _ <- updateGrouplessAddressColumn("token_tx_per_addresses", "address", "groupless_address")
        _ <- updateGrouplessAddressColumn(
          "transaction_per_addresses",
          "address",
          "groupless_address"
        )
        _ <- updateGrouplessAddressColumn(
          "inputs",
          "output_ref_address",
          "output_ref_groupless_address",
          "chain_from"
        )
      } yield {
        logger.info("`addresse_like` migration done")
      }
    }

  /*
   * Those indexes will be re-created concurrently while including the `groupless_address` column
   */
  def migration7(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    for {
      // Renamed and modified to `non_spent_output_groupless_covering_include_idx`
      _ <- sqlu"DROP INDEX IF EXISTS non_spent_output_group_covering_include_idx"
      _ <- addGrouplessAddressColumn("uoutputs", "groupless_address")
      _ <- addGrouplessAddressColumn("uinputs", "groupless_address")
    } yield {
      ()
    }

  }

  def migration9(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    for {
      // Drop index that was failing due to the `UNIQUE` constraint
      _ <- sqlu"DROP INDEX IF EXISTS txs_per_address_uniq_groupless_idx"
      _ <- sqlu"DROP INDEX IF EXISTS token_tx_per_address_groupless_unique_idx"
    } yield {
      ()
    }

  }

  private def migrations(implicit
      explorerConfig: ExplorerConfig,
      ec: ExecutionContext
  ): Seq[DBActionAll[Unit]] = Seq(
    migration1,
    migration2,
    migration3,
    migration4,
    migration5,
    migration6,
    migration7,
    migration8,
    migration9
  )

  def backgroundCoinbaseMigration()(implicit
      ec: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    logger.info(s"Starting coinbase value migration")
    DBRunner
      // First find all blocks containing a tx with 2 different coinbase value
      .run(sql"""
        SELECT DISTINCT t.block_hash
        FROM transaction_per_addresses AS t
        GROUP BY t.block_hash, t.tx_hash
        HAVING COUNT(DISTINCT t.coinbase) = 2;
      """.asAS[BlockHash])
      .flatMap { blocks =>
        logger.info(s"Coinbase value migration started for ${blocks.size} blocks")
        var count = 0
        // scalastyle:off magic.number
        foldFutures(ArraySeq.from(blocks.grouped(1000))) { group =>
          // scalastyle:on magic.number
          DBRunner.run(DBIOAction.sequence(group.map(updateBlockCoinbaseTx))).map { _ =>
            count += group.size
            logger.info(s"Coinbase value migration done for $count blocks")
          }
        }
      }
      .map { _ =>
        logger.info("Coinbase value migration done")
      }
  }

  def updateBlockCoinbaseTx(blockHash: BlockHash): DBActionRWT[Int] = {
    sqlu"""
      BEGIN;

      -- Set coinbase = false for all transactions in the specified block
      UPDATE transaction_per_addresses
      SET coinbase = false
      WHERE block_hash = $blockHash;

      -- Set coinbase = true for the last transaction in the specified block
      WITH LastTransaction AS (
          SELECT MAX(tx_order) AS max_tx_order
          FROM transaction_per_addresses
          WHERE block_hash = $blockHash
      )
      UPDATE transaction_per_addresses AS t
      SET coinbase = true
      FROM LastTransaction AS lt
      WHERE t.block_hash = $blockHash
        AND t.tx_order = lt.max_tx_order;

      COMMIT;
      """
  }

  def migration8(implicit
      explorerConfig: ExplorerConfig,
      ec: ExecutionContext
  ): DBActionAll[Unit] = {
    logger.info("Fix block hashrate")
    for {
      // Fix hashrate for blocks after the Danube Hard Fork
      // We were still using the rohne block timestamp of 16 seconds, while it's now 8 seconds
      _ <- sqlu"""
          UPDATE block_headers
          SET hashrate = hashrate * 2
          WHERE block_timestamp >= ${explorerConfig.consensus.danube.forkTimestamp}
        """
      // Reset average hashrates computed after the Danube Hard Fork
      // Hashrate service will automatically recompute the values
      _ <- sqlu"""
          DELETE FROM hashrates
          WHERE block_timestamp >= ${explorerConfig.consensus.danube.forkTimestamp}
        """
    } yield {
      logger.info("Block hashrate fixed")
    }
  }

  def migrationsQuery(
      versionOpt: Option[MigrationVersion]
  )(implicit explorerConfig: ExplorerConfig, ec: ExecutionContext): DBActionAll[Unit] = {
    logger.info(s"Current migration version: $versionOpt")
    versionOpt match {
      // noop
      case None | Some(MigrationVersion(latestVersion.version)) =>
        logger.info(s"No migrations needed")
        DBIOAction.successful(())
      case Some(MigrationVersion(current)) if current > latestVersion.version =>
        throw new Exception("Incompatible migration versions, please reset your database")
      case Some(MigrationVersion(current)) =>
        logger.info(s"Applying ${latestVersion.version - current} migrations")
        val migrationsToPerform = migrations.drop(current)
        DBIOAction
          .sequence(migrationsToPerform)
          .transactionally
          .map(_ => ())
    }
  }

  def migrateInBackground(
      versionOpt: Option[MigrationVersion]
  )(implicit
      ec: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    (versionOpt match {
      // noop
      case None | Some(MigrationVersion(latestVersion.version)) =>
        logger.info(s"No background migrations needed")
        Future.unit
      case Some(MigrationVersion(current)) if current > latestVersion.version =>
        throw new Exception("Incompatible migration versions, please reset your database")
      case Some(MigrationVersion(current)) =>
        if (current <= 5) {
          logger.info(s"Background migrations needed, but will be done in a future release")
          /*
           * The coinbase migration is heavy and we had some performance issues due to the increase of users.
           * First, we need to optimize some queries in the syncing process, and then we can re-enable this migration.
           * For now, we will just update the version and perform the migration in the next release.
           * We can't just remove it and revert to the previous version because some users might have already completed the migration.
           */
          // backgroundCoinbaseMigration()
          Future.unit
        } else {
          Future.unit
        }
    }).flatMap(_ => DBRunner.run(updateVersion(Some(latestVersion))))
  }

  def migrate(
      databaseConfig: DatabaseConfig[PostgresProfile]
  )(implicit explorerConfig: ExplorerConfig, ec: ExecutionContext): Future[Unit] = {
    logger.info("Migrating")
    for {
      currentVersion <- DBRunner
        .run(databaseConfig)(for {
          version <- getVersion()
          _       <- migrationsQuery(version)
        } yield version)
    } yield {
      migrateInBackground(currentVersion)(ec, databaseConfig).onComplete {
        case scala.util.Success(_) => logger.info("Background migration completed successfully")
        case scala.util.Failure(exception) => logger.error("Background migration failed", exception)
      }
    }
  }

  def getVersion()(implicit ec: ExecutionContext): DBActionAll[Option[MigrationVersion]] = {
    AppStateQueries.get(MigrationVersion)
  }

  def updateVersion(
      versionOpt: Option[MigrationVersion]
  )(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    versionOpt match {
      case None => DBIOAction.successful(())
      case Some(version) =>
        AppStateQueries
          .insertOrUpdate(version)
          .map(_ => ())
    }
  }
}
