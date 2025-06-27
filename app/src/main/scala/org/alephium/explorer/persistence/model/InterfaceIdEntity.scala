// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model.StdInterfaceId

sealed trait InterfaceIdEntity {
  val id: String

  def toApi: StdInterfaceId = StdInterfaceId.from(id)
}

object InterfaceIdEntity {
  final case class StdInterfaceIdEntity(id: String) extends InterfaceIdEntity
  final case object NoInterfaceIdEntity extends InterfaceIdEntity {
    val id: String = ""
  }

  def from(str: String): InterfaceIdEntity =
    if (str == "") {
      NoInterfaceIdEntity
    } else {
      StdInterfaceIdEntity(str)
    }
}
