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

package org.alephium.explorer.api.model

import sttp.tapir._

import org.alephium.explorer.api.Json._
import org.alephium.explorer.api.Schemas.tokenIdSchema
import org.alephium.json.Json._
import org.alephium.protocol.model.TokenId

final case class TokenInfo(token: TokenId, stdInterfaceId: Option[TokenStdInterfaceId])

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
