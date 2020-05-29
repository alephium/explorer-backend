package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.api.Schemas._
import org.alephium.explorer.api.model.{BlockEntry, TimeInterval}
import org.alephium.util.TimeStamp

trait BlockEndpoints extends BaseEndoint {

  private val timeIntervalQuery: EndpointInput[TimeInterval] = query[TimeStamp]("fromTs")
    .and(query[TimeStamp]("toTs"))
    .validate(Validator.custom({ case (from, to) => from <= to }, "`fromTs` must be before `toTs`"))
    .map({ case (from, to) => TimeInterval(from, to) })(timeInterval =>
      (timeInterval.from, timeInterval.to))

  val getBlockById: Endpoint[String, ApiError, BlockEntry, Nothing] =
    baseEndpoint.get
      .in("blocks" / path[String]("blockID"))
      .out(jsonBody[BlockEntry])

  val listBlocks: Endpoint[TimeInterval, ApiError, Seq[BlockEntry], Nothing] =
    baseEndpoint.get
      .in("blocks")
      .in(timeIntervalQuery)
      .out(jsonBody[Seq[BlockEntry]])
}
