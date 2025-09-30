// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.CodecFormat.TextPlain

import org.alephium.api.TapirCodecs
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.api.Codecs._
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
      limit: Int = Pagination.defaultLimit,
      maxLimit: Int = Pagination.maxLimit
  ): EndpointInput[Pagination] =
    paginatorParams(limit, maxLimit)
      .map { case (page, limit) => Pagination.unsafe(page, limit) }(p => (p.page, p.limit))

  private def paginatorParams(
      limit: Int = Pagination.defaultLimit,
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
            case None        => limit
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

  val optionalTimeIntervalQuery: EndpointInput[(Option[TimeStamp], Option[TimeStamp])] =
    query[Option[TimeStamp]]("fromTs")
      .description("inclusive")
      .and(query[Option[TimeStamp]]("toTs").description("exclusive"))
      .validate(Validator.custom {
        case (Some(fromTs), Some(toTs)) if fromTs >= toTs =>
          ValidationResult.Invalid(s"`fromTs` must be before `toTs`")
        case _ =>
          ValidationResult.Valid
      })

  val intervalTypeQuery: EndpointInput[IntervalType] =
    query[IntervalType]("interval-type")

  val optionalTxStatusTypeQuery: EndpointInput[Option[TxStatusType]] =
    query[Option[TxStatusType]]("status")

  val tokenInterfaceIdQuery: EndpointInput[Option[TokenStdInterfaceId]] =
    query[Option[TokenStdInterfaceId]]("interface-id")
      .description(
        s"${StdInterfaceId.tokenStdInterfaceIds.map(_.value).mkString(", ")} or any interface id in hex-string format, e.g: 0001"
      )
      .schema(StdInterfaceId.tokenWithHexStringSchema.format("string").asOption)
}
