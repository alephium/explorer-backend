package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.api.Schemas._
import org.alephium.explorer.api.model.{BlockEntry, TimeInterval}
import org.alephium.util.TimeStamp

trait BlockEndpoints {

  private val timeIntervalQuery: EndpointInput[TimeInterval] = query[TimeStamp]("fromTs")
    .and(query[TimeStamp]("toTs"))
    .mapDecode {
      case (from, to) =>
        TimeInterval.validate(from, to) match {
          case Right(timeInterval) => DecodeResult.Value(timeInterval)
          case Left(error)         => DecodeResult.Error(s"from: $from - to: $to", new Throwable(error))
        }
    }(timeInterval => (timeInterval.from, timeInterval.to))

  val getBlockById: Endpoint[String, String, BlockEntry, Nothing] =
    endpoint.get
      .in("blocks" / path[String]("blockID"))
      .out(jsonBody[BlockEntry])
      .errorOut(plainBody[String])

  val listBlocks: Endpoint[TimeInterval, String, Seq[BlockEntry], Nothing] =
    endpoint.get
      .in("blocks")
      .in(timeIntervalQuery)
      .out(jsonBody[Seq[BlockEntry]])
      .errorOut(plainBody[String])
}
