// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.persistence.DBActionR
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries._
import org.alephium.explorer.util.SlickUtil._

// scalastyle:off magic.number
object IndexChecker {

  /** Run query checks */
  def check()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[ExplainResult]] =
    run(checkAction())

  def checkAction()(implicit ec: ExecutionContext): DBActionR[ArraySeq[ExplainResult]] =
    for {
      a <- BlockQueries.explainListMainChainHeadersWithTxnNumber(
        Pagination.Reversible.unsafe(1, 20)
      ) // first page
      b <- BlockQueries.explainListMainChainHeadersWithTxnNumber(
        Pagination.Reversible.unsafe(10000, 20)
      ) // far page
      c                  <- BlockQueries.explainMainChainQuery()
      oldestOutputEntity <- OutputQueries.getMainChainOutputs(true).headOrEmpty
      latestOutputEntity <- OutputQueries.getMainChainOutputs(false).headOrEmpty
      d                 <- OutputQueries.explainGetTxnHash(oldestOutputEntity.map(_.key).headOption)
      e                 <- OutputQueries.explainGetTxnHash(latestOutputEntity.map(_.key).headOption)
      oldestInputEntity <- InputQueries.getMainChainInputs(true).headOrEmpty
      latestInputEntity <- InputQueries.getMainChainInputs(false).headOrEmpty
      f                 <- InputQueries.explainInputsFromTxs(oldestInputEntity.map(_.hashes()))
      g                 <- InputQueries.explainInputsFromTxs(latestInputEntity.map(_.hashes()))
    } yield ArraySeq(a, b, c, d, e, f, g).sortBy(_.passed)

}
