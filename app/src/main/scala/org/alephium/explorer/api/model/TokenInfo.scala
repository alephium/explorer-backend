// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import sttp.tapir._

import org.alephium.api.TapirSchemas.tokenIdSchema
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.TokenId

final case class TokenInfo(
    token: TokenId,
    stdInterfaceId: Option[TokenStdInterfaceId],
    interfaceId: Option[String],
    category: Option[String]
)

object TokenInfo {

  implicit val readWriter: ReadWriter[TokenInfo] = macroRW

  implicit val schema: Schema[TokenInfo] = Schema[TokenInfo](
    SchemaType.SProduct(
      List(
        SchemaType.SProductField(FieldName("token", "token"), tokenIdSchema, _ => None),
        SchemaType.SProductField(
          FieldName("stdInterfaceId", "stdInterfaceId"),
          StdInterfaceId.tokenWithHexStringSchema.asOption,
          _ => None
        )
      )
    )
  ).name(Schema.SName("TokenInfo"))
}
