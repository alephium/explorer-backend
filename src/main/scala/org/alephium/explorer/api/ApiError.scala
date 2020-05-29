package org.alephium.explorer.api

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import sttp.model.StatusCode
import sttp.tapir.Schema
import sttp.tapir.SchemaType.{SObjectInfo, SProduct}

sealed trait ApiError {
  def status: StatusCode
  def detail: String
}

object ApiError {

  private val apiErrorSchemaFields =
    List("status" -> Schema.schemaForInt, "detail" -> Schema.schemaForString)

  private def encodeApiError[A <: ApiError]: Encoder[A] = new Encoder[A] {
    final def apply(apiError: A): Json = Json.obj(
      ("status", Json.fromInt(apiError.status.code)),
      ("detail", Json.fromString(apiError.detail))
    )
  }

  final case class NotFound(resourceId: String) extends ApiError {
    final val detail: String     = s"Resource with id $resourceId not found"
    final val status: StatusCode = StatusCode.NotFound
  }

  object NotFound {
    implicit val encoder: Encoder[NotFound] = new Encoder[NotFound] {
      val baseEncoder = deriveEncoder[NotFound]
      final def apply(notFound: NotFound): Json =
        encodeApiError[NotFound](notFound).deepMerge(baseEncoder(notFound))
    }

    implicit val decoder: Decoder[NotFound] = deriveDecoder
    implicit val schema: Schema[NotFound] = Schema(
      SProduct(SObjectInfo("NotFound"),
               apiErrorSchemaFields :+ ("resourceId" -> Schema.schemaForString)))
  }

  final case class BadRequest(val detail: String) extends ApiError {
    final val status: StatusCode = StatusCode.BadRequest
  }
  object BadRequest {
    implicit val encoder: Encoder[BadRequest] = encodeApiError[BadRequest]
    implicit val decoder: Decoder[BadRequest] = deriveDecoder
    implicit val schema: Schema[BadRequest] = Schema(
      SProduct(SObjectInfo("BadRequest"), apiErrorSchemaFields))
  }

  implicit val decoder: Decoder[ApiError] = new Decoder[ApiError] {
    def dec(c: HCursor, status: StatusCode): Decoder.Result[ApiError] = status match {
      case StatusCode.BadRequest => BadRequest.decoder(c)
      case StatusCode.NotFound   => NotFound.decoder(c)
      case _                     => Left(DecodingFailure(s"$status not supported", c.history))
    }
    final def apply(c: HCursor): Decoder.Result[ApiError] =
      for {
        statusAsInt <- c.downField("status").as[Int]
        apiError    <- dec(c, StatusCode(statusAsInt))
      } yield apiError
  }

  implicit val encoder: Encoder[ApiError] = new Encoder[ApiError] {
    final def apply(apiError: ApiError): Json = apiError match {
      case badRequest: BadRequest => BadRequest.encoder(badRequest)
      case notFound: NotFound     => NotFound.encoder(notFound)
      case _                      => encodeApiError[ApiError](apiError)
    }
  }

  @SuppressWarnings(
    Array("org.wartremover.warts.JavaSerializable",
          "org.wartremover.warts.Product",
          "org.wartremover.warts.Serializable"))
  implicit val schema: Schema[ApiError] =
    Schema.oneOf[ApiError, StatusCode](_.status, _.toString)(
      StatusCode.BadRequest -> BadRequest.schema,
      StatusCode.NotFound   -> NotFound.schema
    )
}
