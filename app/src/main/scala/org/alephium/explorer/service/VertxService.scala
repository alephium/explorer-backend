// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.Vertx
import sttp.tapir.server.vertx.VertxFutureServerInterpreter._

import org.alephium.util.Service

class VertxService(implicit val executionContext: ExecutionContext)
    extends Service
    with StrictLogging {

  private var _vertx: Vertx = _

  def vertx: Vertx = _vertx

  override def startSelfOnce(): Future[Unit] = {
    _vertx = Vertx.vertx()
    logger.debug("Vertx instance created")
    Future.successful(())
  }

  override def stopSelfOnce(): Future[Unit] = {
    if (_vertx != null) {
      _vertx.close().asScala.map { _ =>
        logger.debug("Vertx instance closed")
        ()
      }
    } else {
      Future.successful(())
    }
  }

  override def subServices: ArraySeq[Service] = ArraySeq.empty
}
