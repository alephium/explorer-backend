package org.alephium.explorer.api

import sttp.tapir.Schema

import org.alephium.util.{AVector, TimeStamp}

object Schemas {
  implicit def avectorSchema[T: Schema]: Schema[AVector[T]] = implicitly[Schema[T]].asArrayElement
  implicit val timestampSchema: Schema[TimeStamp]           = Schema(Schema.schemaForLong.schemaType)
}
