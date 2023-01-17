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
  def check()(implicit ec: ExecutionContext,
              dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[ExplainResult]] =
    run(checkAction())

  def checkAction()(implicit ec: ExecutionContext): DBActionR[ArraySeq[ExplainResult]] =
    for {
      a                  <- BlockQueries.explainListMainChainHeadersWithTxnNumber(Pagination.unsafe(1, 20)) //first page
      b                  <- BlockQueries.explainListMainChainHeadersWithTxnNumber(Pagination.unsafe(10000, 20)) //far page
      c                  <- BlockQueries.explainMainChainQuery()
      oldestOutputEntity <- OutputQueries.getMainChainOutputs(true).headOrEmpty
      latestOutputEntity <- OutputQueries.getMainChainOutputs(false).headOrEmpty
      d                  <- OutputQueries.explainGetTxnHash(oldestOutputEntity.map(_.key).headOption)
      e                  <- OutputQueries.explainGetTxnHash(latestOutputEntity.map(_.key).headOption)
      oldestInputEntity  <- InputQueries.getMainChainInputs(true).headOrEmpty
      latestInputEntity  <- InputQueries.getMainChainInputs(false).headOrEmpty
      f                  <- InputQueries.explainInputsFromTxsNoJoin(oldestInputEntity.map(_.hashes()))
      g                  <- InputQueries.explainInputsFromTxsNoJoin(latestInputEntity.map(_.hashes()))
    } yield ArraySeq(a, b, c, d, e, f, g).sortBy(_.passed)

}
