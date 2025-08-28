// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import io.vertx.ext.web._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.ApiError
import org.alephium.explorer.foldFutures
import org.alephium.explorer.RichAVector._
import org.alephium.explorer.api.TransactionEndpoints
import org.alephium.explorer.api.model._
import org.alephium.explorer.service.TransactionService
import org.alephium.protocol
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
      deserialize[protocol.model.UnsignedTransaction](Hex.unsafe(decodeUTX.unsignedTx)) match {
        case Right(utx) =>
          foldFutures(utx.inputs.toArraySeq) { input =>
            val key = input.outputRef.key.value
            TransactionService
              .getInputFromOutputRef(key)
              .map {
                case Some(output) =>
                  Input(
                    OutputRef(input.outputRef.hint.value, key),
                    Some(serialize(input.unlockScript)),
                    txHashRef = Some(output.txHash),
                    address = Some(output.address),
                    attoAlphAmount = Some(output.amount),
                    tokens = output.tokens,
                    contractInput = false
                  )
                case None =>
                  Input(
                    OutputRef(input.outputRef.hint.value, key),
                    Some(serialize(input.unlockScript)),
                    txHashRef = None,
                    address = None,
                    attoAlphAmount = None,
                    tokens = None,
                    contractInput = false
                  )
              }
          }.map { inputs =>
            Right(
              UnsignedTx(
                utx.id,
                utx.version,
                utx.networkId.id,
                utx.scriptOpt.map(script => Hex.toHexString(serialize(script))),
                utx.gasAmount.value,
                utx.gasPrice.value,
                inputs,
                utx.fixedOutputs.toArraySeq.zipWithIndex.map { case (o, index) =>
                  AssetOutput(
                    hint = o.hint.value,
                    key = protocol.model.TxOutputRef.key(utx.id, index).value,
                    attoAlphAmount = o.amount,
                    address = protocol.model.Address.Asset(o.lockupScript),
                    tokens = Some(o.tokens.toArraySeq.map { case (id, amount) =>
                      Token(id, amount)
                    }),
                    lockTime = Some(o.lockTime),
                    message = Some(o.additionalData),
                    spent = None,
                    fixedOutput = true
                  )
                }
              )
            )
          }
        case Left(err) => Future.successful(Left(ApiError.BadRequest(err.getMessage)))
      }
    })
  )

}
