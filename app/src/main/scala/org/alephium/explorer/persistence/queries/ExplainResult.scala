// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import org.alephium.json.Json._

object ExplainResult {
  implicit val readWriter: ReadWriter[ExplainResult] = macroRW

  /** Indicates an empty input. Explain cannot be executed on parametric queries with no parameters
    */
  def emptyInput(queryName: String): ExplainResult =
    new ExplainResult(
      queryName = queryName,
      queryInput = "empty",
      explain = Vector.empty,
      messages = Vector("Empty input"),
      passed = false
    )

}

final case class ExplainResult(
    queryName: String,
    queryInput: String,
    explain: Vector[String],
    messages: Iterable[String],
    passed: Boolean
)
