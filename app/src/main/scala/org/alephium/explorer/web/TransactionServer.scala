// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import io.vertx.ext.web._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.StatusCode

import org.alephium.api.ApiError
import org.alephium.explorer.api.TransactionEndpoints
import org.alephium.explorer.api.model.DecodeUnsignedTxResult
import org.alephium.explorer.service.TransactionService
import org.alephium.protocol
import org.alephium.protocol.model.GroupIndex
import org.alephium.serde._
import org.alephium.util.Hex

class TransactionServer(implicit
    val executionContext: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile]
) extends Server
    with TransactionEndpoints {
  val routes: ArraySeq[Router => Route] = ArraySeq(
    route(getTransactionById.serverLogic[Future] { hash =>
      TransactionService
        .getTransaction(hash)
        .map(_.toRight(ApiError.NotFound(hash.value.toHexString)))
    }),
    route(decodeUnsignedTx.serverLogic[Future] { decodeUTX =>
      (for {
        utx       <- deserializeUnsignedTx(decodeUTX.unsignedTx)
        fromGroup <- fromGroup(utx)
        toGroup   <- toGroup(utx)
      } yield {
        (fromGroup, toGroup, utx)
      }) match {
        case Right((fromGroup, toGroup, utx)) =>
          TransactionService.convertProtocolUnsignedTx(utx).map { unsignedTx =>
            Right(DecodeUnsignedTxResult(fromGroup, toGroup, unsignedTx))
          }
        case Left(err) => Future.successful(Left(err))
      }
    }),
    route(listTransactions.serverLogic[Future] { case (status, pagination) =>
      TransactionService
        .list(pagination, status)
        .map(Right(_))
    })
  )

  private def fromGroup(
      utx: protocol.model.UnsignedTransaction
  ): Either[ApiError[_ <: StatusCode], GroupIndex] =
    Try(utx.fromGroup).toEither.left.map(_ =>
      ApiError.BadRequest("Cannot get `fromGroup` from inputs")
    )

  private def toGroup(
      utx: protocol.model.UnsignedTransaction
  ): Either[ApiError[_ <: StatusCode], GroupIndex] =
    Try(utx.toGroup).toEither.left.map(_ => ApiError.BadRequest("Cannot get `toGroup` from inputs"))

  private def deserializeUnsignedTx(
      rawUtx: String
  ): Either[ApiError[_ <: StatusCode], protocol.model.UnsignedTransaction] =
    deserialize[protocol.model.UnsignedTransaction](
      Hex.unsafe(rawUtx)
    ).left.map(e => ApiError.BadRequest(e.getMessage))

}
