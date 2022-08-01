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

package org.alephium.explorer.service

import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.persistence.DBActionR
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.{BlockQueries, ExplainResult, OutputQueries}

// scalastyle:off magic.number
object IndexChecker {

  /** Run query checks */
  def check()(implicit ec: ExecutionContext,
              dc: DatabaseConfig[PostgresProfile]): Future[Seq[ExplainResult]] =
    run(checkAction())

  def checkAction()(implicit ec: ExecutionContext): DBActionR[Seq[ExplainResult]] =
    for {
      a                  <- BlockQueries.explainListMainChainHeadersWithTxnNumber(Pagination.unsafe(0, 20)) //first page
      b                  <- BlockQueries.explainListMainChainHeadersWithTxnNumber(Pagination.unsafe(10000, 20)) //far page
      c                  <- BlockQueries.explainMainChainQuery()
      oldestOutputEntity <- OutputQueries.getMainChainOutputs(ascendingOrder = true).result.head
      latestOutputEntity <- OutputQueries.getMainChainOutputs(ascendingOrder = false).result.head
      d                  <- OutputQueries.explainGetTxnHash(oldestOutputEntity.key)
      e                  <- OutputQueries.explainGetTxnHash(latestOutputEntity.key)
    } yield Seq(a, b, c, d, e).sortBy(_.passed)

}
