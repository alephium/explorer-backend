// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.config

import scala.util.{Failure, Random, Success}

import org.alephium.explorer.GroupSetting
import org.alephium.protocol.config.GroupConfig
import org.alephium.util.Env

object Default {

  implicit val groupConfig: GroupConfig =
    Env.resolve() match {
      case Env.Prod =>
        val typesafeConfig = ExplorerConfig.loadConfig(Platform.getRootPath()) match {
          case Success(config) => config
          case Failure(error)  => throw error
        }
        val config: ExplorerConfig = ExplorerConfig.load(typesafeConfig)
        GroupSetting(config.groupNum).groupConfig
      case _ =>
        GroupSetting(Random.between(2, 5)).groupConfig
    }
}
