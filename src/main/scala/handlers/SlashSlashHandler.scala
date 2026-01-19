// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots
package handlers

import xyz.nicebots.FileHandler

/** Handles file types that use // line comments for headers. */
class SlashSlashHandler extends FileHandler:
  /** Supported file extensions.
    *
    * @return
    *   extension set without dots
    */
  override def extensions: Set[String] =
    Set(
      "js",
      "jsx",
      "ts",
      "tsx",
      "java",
      "scala",
      "kt",
      "kts",
      "rs",
      "go",
      "c",
      "cpp",
      "cc",
      "cxx",
      "h",
      "hpp",
      "cs",
      "swift",
      "dart",
      "php"
    )

  /** Render a // comment header.
    *
    * @param raw
    *   plain-text license content
    * @return
    *   rendered header ending with a newline
    */
  override def generateLicense(raw: String): String =
    val rendered = raw.linesIterator
      .map { line =>
        if line.trim.isEmpty then "//"
        else s"// $line"
      }
      .mkString("\n")
    rendered + "\n"
