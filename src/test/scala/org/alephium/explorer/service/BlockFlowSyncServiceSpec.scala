package org.alephium.explorer.service

import scala.concurrent.{ExecutionContext, Future}

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}

import org.alephium.explorer.{Generators, Hash}
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, TimeInterval}
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.db.{DatabaseFixture, DbBlockDao}
import org.alephium.explorer.service.BlockFlowClient.{ChainInfo, HashesAtHeight}
import org.alephium.util.{AlephiumSpec, Duration, TimeStamp}

class BlockFlowSyncServiceSpec extends AlephiumSpec with ScalaFutures with Eventually {
  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Minutes))

  it should "start/sync/stop" in new Fixture {
    val blockFlowSyncService =
      BlockFlowSyncService(groupNum, syncPeriod = Duration.unsafe(2000), blockFlowClient, blockDao)

    checkBlocks(Seq.empty)

    blockFlowSyncService.start().futureValue is ()

    eventually(checkBlocks(blocks))

    blocks = blockFlow.flatten

    eventually(checkBlocks(blocks))

    blockFlowSyncService.stop().futureValue is ()

    databaseConfig.db.close
  }

  trait Fixture extends DatabaseFixture with Generators {
    implicit val executionContext: ExecutionContext = ExecutionContext.global

    val groupNum: Int = 4

    val startTimestamp: TimeStamp = TimeStamp.now
    val endTimestamp: TimeStamp   = startTimestamp.plusMinutesUnsafe(10)

    val blockFlow: Seq[Seq[BlockEntry]] =
      blockFlowGen(groupNum = groupNum, maxChainSize = 20, startTimestamp = startTimestamp).sample.get
    val blockDao: BlockDao = new DbBlockDao(databaseConfig)

    var blocks: Seq[BlockEntry] = blockFlow.flatMap { chain =>
      if (chain.size <= 1) {
        chain
      } else {
        chain.splitAt(chain.size / 2)._1
      }
    }
    val blockFlowClient: BlockFlowClient = new BlockFlowClient {
      def getBlock(hash: Hash): Future[Either[String, BlockEntry]] =
        Future.successful(blocks.find(_.hash === hash).toRight(s"$hash Not Found"))

      def getChainInfo(from: GroupIndex, to: GroupIndex): Future[Either[String, ChainInfo]] =
        Future.successful(
          Right(
            ChainInfo(
              blocks
                .filter(block => block.chainFrom === from && block.chainTo === to)
                .map(_.height)
                .max)))

      def getHashesAtHeight(from: GroupIndex,
                            to: GroupIndex,
                            height: Int): Future[Either[String, HashesAtHeight]] =
        Future.successful(
          Right(
            HashesAtHeight(
              blocks
                .filter(block =>
                  block.chainFrom === from && block.chainTo === to && block.height === height)
                .map(_.hash))))
    }

    def checkBlocks(blocksToCheck: Seq[BlockEntry]) =
      blockDao
        .list(TimeInterval(startTimestamp, endTimestamp))
        .futureValue
        .toSet is blocksToCheck.toSet
  }
  // scalastyle:on scalatest-matcher
}
