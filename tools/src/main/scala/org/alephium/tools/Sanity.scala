// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.tools

import java.time.Instant
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util._
import scala.concurrent.duration.{Duration=>SDuration}

import com.typesafe.config.ConfigFactory
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{foldFutures, sideEffect}
import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.config._
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence.DBRunner
import org.alephium.explorer.persistence.queries.BlockQueries
import org.alephium.explorer.persistence.schema.BlockHeaderSchema
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.GroupSetting
import org.alephium.protocol.ALPH
import org.alephium.explorer.service.BlockService

import org.alephium.util._
import org.alephium.explorer.util.TimeUtil

@SuppressWarnings(
  Array("org.wartremover.warts.GlobalExecutionContext","org.wartremover.warts.OptionPartial", "org.wartremover.warts.TryPartial"))
object Sanity {
  def main(args: Array[String]): Unit = {
  val typesafeConfig    = ConfigFactory.load()

  val applicationConfig = ApplicationConfig(typesafeConfig).get

  implicit val config: ExplorerConfig =
    ExplorerConfig(applicationConfig).get

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  implicit val databaseConfig: DatabaseConfig[PostgresProfile] =
    DatabaseConfig.forConfig[PostgresProfile]("db", typesafeConfig)

  val blockFlowClient: BlockFlowClient =
    BlockFlowClient(
      uri         = config.blockFlowUri,
      groupNum    = config.groupNum,
      maybeApiKey = config.maybeBlockFlowApiKey
    )

  implicit lazy val groupSettings: GroupSetting =
    GroupSetting(config.groupNum)

  implicit lazy val blockCache: BlockCache =
    BlockCache()(groupSettings, executionContext, databaseConfig)

    val blockService = BlockService
  val timeRanges = TimeUtil.buildTimestampRange(ALPH.LaunchTimestamp, TimeStamp.now, Duration.ofDaysUnsafe(1))

  val pagination =  Pagination.unsafe(0, Int.MaxValue)

  var i = 0
  try{
  val done = Await.result(
  foldFutures(timeRanges){case (from,to) =>
    val fromInstant = Instant.ofEpochMilli(from.millis)
    i = 0
    println(s"${Console.RED}${Console.BOLD}*** fromInstant ***\n\t${Console.RESET}${fromInstant}")
    DBRunner.run(BlockHeaderSchema.table.filter(b => b.timestamp >= from && b.timestamp < to).map(b=>(b.hash, b.chainFrom)).result).flatMap { hashes =>
      Future.sequence(hashes.map{ case (hash, chainFrom) =>
        for{
        nodeBlock <- blockFlowClient.fetchBlock(chainFrom, hash)
        block <- blockService.getLiteBlockByHash(hash).map(_.get)
        transactions <- blockService.getBlockTransactions(hash, pagination)
        } yield {
          assert(nodeBlock.timestamp == block.timestamp)
          assert(nodeBlock.chainFrom == block.chainFrom)
          assert(nodeBlock.chainTo == block.chainTo)
          assert(nodeBlock.height == block.height)
          assert(nodeBlock.transactions.lengthIs == block.txNumber)
          assert(nodeBlock.hashrate == block.hashRate)
          transactions.foreach {tx =>
            val nodeInputs = nodeBlock.inputs.filter(_.txHash == tx.hash).sortBy(_.inputOrder)
            val nodeOutputs = nodeBlock.outputs.filter(_.txHash == tx.hash).sortBy(_.outputOrder)

            assert(tx.inputs.lengthIs == nodeInputs.length)
            assert(tx.outputs.lengthIs == nodeOutputs.length)

            tx.inputs.zip(nodeInputs).foreach { case (in, nodeIn) =>
              assert(in.outputRef.hint == nodeIn.hint)
              assert(in.outputRef.key == nodeIn.outputRefKey)
              assert(in.unlockScript == nodeIn.unlockScript)
            }

            tx.outputs.zip(nodeOutputs).foreach { case (out, nodeOut) =>
              assert(out.hint == nodeOut.hint)
              assert(out.key == nodeOut.key)
              assert(out.attoAlphAmount == nodeOut.amount)
              assert(out.address == nodeOut.address)
              assert(out.tokens == nodeOut.tokens)
            }
          }

          i = i+1
          if(i %1000 == 0){
            println(i)
          }
        }
      })
    }
  }.map(_=>()), SDuration.Inf)
    println(s"${Console.RED}${Console.BOLD}*** done ***\n\t${Console.RESET}${done}")
  } catch {
      case error: Throwable =>
        println(s"${Console.RED}${Console.BOLD}*** error ***\n\t${Console.RESET}${error}")
  }
}
}
