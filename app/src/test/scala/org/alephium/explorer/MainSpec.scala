// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.alephium.explorer.config.{BootMode, ExplorerConfig, Platform}

class MainSpec extends AlephiumSpec {

  private def withUserHome(path: String)(body: => Unit): Unit = {
    val previous = sys.props.get("user.home")

    try {
      System.setProperty("user.home", path)
      ()
      body
    } finally {
      previous match {
        case Some(value) =>
          System.setProperty("user.home", value)
          ()
        case None =>
          System.clearProperty("user.home")
          ()
      }
    }
  }

  private def withUserConfig(content: Option[String])(body: => Unit): Unit = {
    val rootPath = Platform.getRootPath()
    val userFile = ExplorerConfig.getUserConfig(rootPath)
    val previous =
      if (userFile.exists()) Some(Files.readString(userFile.toPath)) else None

    def restore(): Unit = {
      previous match {
        case Some(value) =>
          Files.writeString(userFile.toPath, value, StandardCharsets.UTF_8)
          ()
        case None =>
          Files.deleteIfExists(userFile.toPath)
          ()
      }
    }

    try {
      content match {
        case Some(value) =>
          Files.writeString(userFile.toPath, value, StandardCharsets.UTF_8)
          ()
        case None =>
          Files.deleteIfExists(userFile.toPath)
          ()
      }
      body
    } finally {
      restore()
    }
  }

  "BootUp" should {
    "load the default config from the root path" in {
      val homePath = Files.createTempDirectory("explorer-home")
      withUserHome(homePath.toString) {
        withUserConfig(None) {
          val bootUp = new BootUp
          bootUp.config.bootMode is BootMode.ReadWrite
          ()
        }
      }
    }
  }

  "Main.main" should {
    "swallow boot errors and return" in {
      val homePath = Files.createTempDirectory("explorer-home")
      withUserHome(homePath.toString) {
        withUserConfig(Some("not-valid-hocon")) {
          Main.main(Array.empty)
        }
      }
    }
  }
}
