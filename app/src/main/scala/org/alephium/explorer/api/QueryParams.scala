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

package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.CodecFormat.TextPlain

import org.alephium.api.TapirCodecs
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.api.Schemas._
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.TokenId
import org.alephium.util.{Duration, TimeStamp}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
trait QueryParams extends TapirCodecs {

  implicit val tokenIdTapirCodec: Codec[String, TokenId, TextPlain] =
    fromJson[TokenId]

  val pagination: EndpointInput[Pagination] =
    paginator()

  val paginationReversible: EndpointInput[Pagination.Reversible] =
    paginatorParams()
      .and(
        query[Option[Boolean]]("reverse")
          .description("Reverse pagination")
          .map {
            case Some(reverse) => reverse
            case None          => false
          }(Some(_))
      )
      .map { case (page, limit, reverse) => Pagination.Reversible.unsafe(page, limit, reverse) }(
        p => (p.page, p.limit, p.reverse)
      )

  def paginator(
      defaultLimit: Int = Pagination.defaultLimit,
      maxLimit: Int = Pagination.maxLimit
  ): EndpointInput[Pagination] =
    paginatorParams(defaultLimit, maxLimit)
      .map { case (page, limit) => Pagination.unsafe(page, limit) }(p => (p.page, p.limit))

  private def paginatorParams(
      defaultLimit: Int = Pagination.defaultLimit,
      maxLimit: Int = Pagination.maxLimit
  ): EndpointInput[(Int, Int)] =
    query[Option[Int]]("page")
      .description("Page number")
      .map {
        case Some(page) => page
        case None       => Pagination.defaultPage
      }(Some(_))
      .validate(Validator.min(1))
      .and(
        query[Option[Int]]("limit")
          .description("Number of items per page")
          .map {
            case Some(limit) => limit
            case None        => defaultLimit
          }(Some(_))
          .validate(Validator.min(0))
          .validate(Validator.max(maxLimit))
      )

  val timeIntervalQuery: EndpointInput[TimeInterval] =
    query[TimeStamp]("fromTs")
      .and(query[TimeStamp]("toTs"))
      .map { case (from, to) => TimeInterval(from, to) }(timeInterval =>
        (timeInterval.from, timeInterval.to)
      )
      .validate(TimeInterval.validator)

  def timeIntervalWithMaxQuery(duration: Duration): EndpointInput[TimeInterval] =
    timeIntervalQuery
      .validate(Validator.custom { timeInterval =>
        if (timeInterval.durationUnsafe() > duration) {
          ValidationResult.Invalid(s"Time interval cannot be greater than: $duration")
        } else {
          ValidationResult.Valid
        }
      })

  val intervalTypeQuery: EndpointInput[IntervalType] =
    query[IntervalType]("interval-type")

  val tokenInterfaceIdQuery: EndpointInput[Option[TokenStdInterfaceId]] =
    query[Option[TokenStdInterfaceId]]("interface-id")
      .description(
        s"${StdInterfaceId.tokenStdInterfaceIds.map(_.value).mkString(", ")} or any interface id in hex-string format, e.g: 0001"
      )
      .schema(StdInterfaceId.tokenWithHexStringSchema.format("string").asOption)
}
