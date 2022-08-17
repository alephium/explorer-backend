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

package org.alephium.explorer.persistence.queries

import org.alephium.json.Json._

object ExplainResult {
  implicit val readWriter: ReadWriter[ExplainResult] = macroRW

  /** Indicates an empty input. Explain cannot be executed on parametric queries with no parameters */
  def emptyInput(queryName: String): ExplainResult =
    new ExplainResult(queryName  = queryName,
                      queryInput = "empty",
                      explain    = Vector.empty,
                      messages   = Vector("Empty input"),
                      passed     = false)

}

final case class ExplainResult(queryName: String,
                               queryInput: String,
                               explain: Vector[String],
                               messages: Iterable[String],
                               passed: Boolean)
