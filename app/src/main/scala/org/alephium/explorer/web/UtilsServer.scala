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

package org.alephium.explorer.web

import scala.concurrent.{ExecutionContext, Future}
import scala.util._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import org.slf4j.LoggerFactory
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.StatusCode

import org.alephium.api.ApiError
import org.alephium.explorer.{sideEffect, GroupSetting}
import org.alephium.explorer.api.UtilsEndpoints
import org.alephium.explorer.api.model.LogbackValue
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.service.{BlockFlowClient, IndexChecker, SanityChecker}

class UtilsServer()(implicit val executionContext: ExecutionContext,
                    dc: DatabaseConfig[PostgresProfile],
                    blockFlowClient: BlockFlowClient,
                    blockCache: BlockCache,
                    groupSetting: GroupSetting)
    extends Server
    with UtilsEndpoints {

  val route: Route =
    toRoute(sanityCheck.serverLogicSuccess[Future] { _ =>
      sideEffect(SanityChecker.check())
      Future.successful(())
    }) ~
      toRoute(indexCheck.serverLogic[Future] { _ =>
        IndexChecker.check().map(Right(_))
      }) ~
      toRoute(changeGlobalLogLevel.serverLogic[Future] { level =>
        Future.successful(updateGlobalLevel(level))
      }) ~
      toRoute(changeLogConfig.serverLogic[Future] { values =>
        Future.successful(updateLoggerContext(values))
      })

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private def updateGlobalLevel(level: String): Either[ApiError[_ <: StatusCode], Unit] = {
    val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger];
    Try(root.setLevel(Level.valueOf(level))) match {
      case Success(_) =>
        logger.info(s"Logging level updated to $level.")
        Right(())
      case Failure(error) =>
        val errorMessage = s"Cannot apply logback configuration: $error"
        logger.error(errorMessage)
        Left(ApiError.BadRequest(errorMessage))
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private def updateLoggerContext(
      values: Seq[LogbackValue]
  ): Either[ApiError[_ <: StatusCode], Unit] = {
    val loggerFactory = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
    loggerFactory match {
      case logbackCtx: LoggerContext =>
        Try {
          values.foreach { logbackValue =>
            val level  = Level.toLevel(logbackValue.level.toString())
            val logger = logbackCtx.getLogger(logbackValue.name)
            logger.info(s"Updating logging level: ${logbackValue.name} -> $level")
            logger.setLevel(level)
          }
        } match {
          case Success(_) =>
            logger.info("Logback configuration updated OK.")
            Right(())
          case Failure(e) =>
            Left(ApiError.BadRequest(s"Cannot apply logback values: ${e.getMessage}"))
        }
      case _ =>
        val message = "Can't update logging configuration, only logback is supported"
        logger.error(message)
        Left(ApiError.BadRequest(message))
    }
  }
}
