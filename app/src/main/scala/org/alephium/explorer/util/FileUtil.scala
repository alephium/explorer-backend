// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
