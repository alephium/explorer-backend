// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.config

import com.typesafe.config.{Config, ConfigFactory}

object TestExplorerConfig {

  def apply(): ExplorerConfig = {
    ExplorerConfig.load(
      typesafeConfig()
    )
  }

  def typesafeConfig(): Config = {
    ConfigFactory.load(
      ConfigFactory.parseResources(s"application-devnet.conf")
    )
  }
}
