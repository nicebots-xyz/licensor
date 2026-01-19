// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots
package handlers

import xyz.nicebots.FileHandler

/** Handles file types that use # line comments for headers. */
class HashtagHandler extends FileHandler:
  /** Supported file extensions.
    *
    * @return
    *   extension set without dots
    */
  override def extensions: Set[String] =
    Set(
      "py",
      "sh",
      "bash",
      "zsh",
      "rb",
      "pl",
      "ps1",
      "psm1",
      "psd1",
      "yml",
      "yaml",
      "toml",
      "ini",
      "cfg",
      "conf"
    )

  /** Render a # comment header.
    *
    * @param raw
    *   plain-text license content
    * @return
    *   rendered header ending with a newline
    */
  override def generateLicense(raw: String): String =
    val rendered = raw.linesIterator
      .map { line =>
        if line.trim.isEmpty then "#"
        else s"# $line"
      }
      .mkString("\n")
    rendered + "\n"
