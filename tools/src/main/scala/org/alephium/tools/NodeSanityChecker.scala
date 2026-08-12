// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.tools

import java.util.concurrent.atomic.AtomicInteger

import scala.collection.immutable.ArraySeq
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.model.BlockEntry
import org.alephium.explorer.config._
import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.queries.BlockQueries
import org.alephium.explorer.util.ExecutionContextUtil
import org.alephium.explorer.util.SlickUtil._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.protocol.model.ChainIndex
import org.alephium.util.discard

object NodeSanityChecker {
  def main(args: Array[String]): Unit = {
    discard(new Runner().run())
  }

  private final class Runner extends StrictLogging {
    private val executor = java.util.concurrent.Executors.newCachedThreadPool()
    private val blockNum  = new AtomicInteger(0)

    private val typesafeConfig = ExplorerConfig.loadConfig(Platform.getRootPath()) match {
      case scala.util.Success(config) => config
      case scala.util.Failure(error)  => throw error
    }

    implicit private val config: ExplorerConfig = ExplorerConfig.load(typesafeConfig)

    implicit private val databaseConfig: DatabaseConfig[PostgresProfile] =
      DatabaseConfig.forConfig[PostgresProfile]("db", typesafeConfig)

    implicit private val executionContext: ExecutionContext =
      ExecutionContextUtil.fromExecutor(executor, ())

    implicit private val groupSetting: GroupSetting = GroupSetting(config.groupNum)

    implicit private val blockFlowClient: BlockFlowClient =
      BlockFlowClient(
        uri = config.blockFlowUri,
        groupNum = config.groupNum,
        maybeApiKey = config.maybeBlockFlowApiKey,
        directCliqueAccess = config.directCliqueAccess,
        consensus = config.consensus
      )

    def run(): Unit = {
      try {
        Await.result(check(), Duration.Inf)
        logger.info("Node sanity checker finished without finding a mismatch")
      } catch {
        case NonFatal(error) =>
          logger.error("Node sanity checker stopped after finding a mismatch", error)
          sys.exit(1)
      } finally {
        databaseConfig.db.close()
        executor.shutdown()
      }
    }

    private def check()(implicit
        blockFlowClient: BlockFlowClient,
        groupSetting: GroupSetting
    ): Future[Unit] = {
      logger.info("Starting node sanity check, counting blocks")
      countBlocks().flatMap { nbOfBlocks =>
        logger.info(s"$nbOfBlocks to check")
        foldFutures(ArraySeq.from(groupSetting.chainIndexes)) { chainIndex =>
          compareChain(chainIndex, nbOfBlocks)
        }.map(_ => ())
      }
    }

    private def countBlocks(): Future[Int] =
      databaseConfig.db.run(
        sql"""
          SELECT count(*)
          FROM block_headers
          WHERE main_chain = true
        """.asAS[Int].exactlyOne
      )

    private def compareChain(
        chainIndex: ChainIndex,
        totalNbOfBlocks: Int
    )(implicit
        blockFlowClient: BlockFlowClient
    ): Future[Unit] = {
      for {
        chainInfo <- blockFlowClient.fetchChainInfo(chainIndex)
        localLatest <- databaseConfig.db.run(BlockQueries.getLatestBlock(chainIndex.from, chainIndex.to).headOrNone)
        _ <- localLatest match {
          case None =>
            logger.info(s"No local latest block found for chain ${describeChain(chainIndex)}")
            Future.unit
          case Some(localTip) =>
            BlockDao.get(localTip.hash).flatMap {
              case None =>
                failProblem(
                  s"Local DB latest block ${localTip.hash.toHexString} is missing from the database for chain ${describeChain(chainIndex)}"
                )
              case Some(block) =>
                compareBlock(chainIndex, chainInfo.currentHeight, block, totalNbOfBlocks)
            }
        }
      } yield ()
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    private def compareBlock(
        chainIndex: ChainIndex,
        nodeHeight: Int,
        localBlock: BlockEntry,
        totalNbOfBlocks: Int
    )(implicit
        blockFlowClient: BlockFlowClient
    ): Future[Unit] = {
      val nextBlockNum = blockNum.incrementAndGet()
      if (nextBlockNum % 10000 == 0) {
        logger.debug(
          s"Checked $nextBlockNum blocks, progress ${(nextBlockNum.toFloat / totalNbOfBlocks * 100.0).toInt}%"
        )
      }

      blockFlowClient.fetchHashesAtHeight(chainIndex, localBlock.height).flatMap { nodeHashes =>
        nodeHashes.headers.headOption match {
          case Some(nodeHash) if localBlock.hash == nodeHash =>
            localBlock.parent match {
              case None =>
                Future.unit
              case Some(parentHash) =>
                BlockDao.get(parentHash).flatMap {
                  case None =>
                    failProblem(
                      s"Local DB is missing parent ${parentHash.toHexString} for chain ${describeChain(chainIndex)} " +
                        s"at height ${localBlock.height.value}"
                    )
                  case Some(parentBlock) =>
                    compareBlock(chainIndex, nodeHeight, parentBlock, totalNbOfBlocks)
                }
            }

          case Some(nodeHash) =>
            failProblem(
              s"Block mismatch for chain ${describeChain(chainIndex)} at height ${localBlock.height.value}: " +
                s"local block ${describeBlock(localBlock)}, node hash ${nodeHash.toHexString} (node height $nodeHeight)"
            )

          case None =>
            failProblem(
              s"Node returned no block hash at height ${localBlock.height.value} for chain ${describeChain(chainIndex)} " +
                s"(local block ${describeBlock(localBlock)}, node height $nodeHeight)"
            )
        }
      }
    }

    private def describeChain(chainIndex: ChainIndex): String =
      s"${chainIndex.from.value}->${chainIndex.to.value}"

    private def describeBlock(block: BlockEntry): String =
      s"${block.hash.toHexString}@${block.height.value}"

    private def failProblem(message: String): Future[Unit] = {
      logger.error(message)
      Future.failed(new IllegalStateException(message))
    }
  }
}
