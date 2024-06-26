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
