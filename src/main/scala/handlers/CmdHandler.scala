// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots
package handlers

import xyz.nicebots.FileHandler

/** Handles .cmd files that use :: line comments. */
class CmdHandler extends FileHandler:
  /** Supported file extensions.
    *
    * @return
    *   extension set without dots
    */
  override def extensions: Set[String] =
    Set(
      "cmd"
    )

  /** Render a :: comment header.
    *
    * @param raw
    *   plain-text license content
    * @return
    *   rendered header ending with a newline
    */
  override def generateLicense(raw: String): String =
    val rendered = raw.linesIterator
      .map { line =>
        if line.trim.isEmpty then "::"
        else s":: $line"
      }
      .mkString("\n")
    rendered + "\n"
