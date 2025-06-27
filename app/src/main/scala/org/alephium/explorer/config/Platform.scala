// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.config

import java.nio.file.{Path, Paths}

import org.alephium.explorer.util.FileUtil
import org.alephium.util.Files

object Platform {

  val defaultHome: String = ".alephium-explorer-backend"

  def getRootPath(): Path = {
    val rootPath =
      sys.env.get("EXPLORER_HOME") match {
        case Some(rawPath) => Paths.get(rawPath)
        case None          => Files.homeDir.resolve(defaultHome)
      }

    FileUtil.createDirIfNotExists(rootPath)

    rootPath
  }
}
