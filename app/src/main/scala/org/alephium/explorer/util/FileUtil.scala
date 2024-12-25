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

package org.alephium.explorer.util

import java.io.File
import java.nio.file.{Files, Path}

import org.alephium.util.discard

object FileUtil {

  def createFileIfNotExists(file: File): Unit = {
    if (!file.exists) {
      discard(file.createNewFile)
    } else {
      ()
    }
  }

  def createDirIfNotExists(path: Path): Unit = {
    if (!Files.exists(path)) {
      discard(path.toFile.mkdir())
    } else {
      ()
    }
  }
}
