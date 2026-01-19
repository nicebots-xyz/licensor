// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots
package handlers

import xyz.nicebots.FileHandler

/** Handles HTML-like files with block comments. */
class HtmlHandler extends FileHandler:
  /** Supported file extensions.
    *
    * @return
    *   extension set without dots
    */
  override def extensions: Set[String] =
    Set(
      "html",
      "htm",
      "xhtml",
      "xml",
      "svg",
      "svelte",
      "md",
      "vue",
      "hbs",
      "handlebars",
      "mustache"
    )

  /** Render an HTML comment header.
    *
    * @param raw
    *   plain-text license content
    * @return
    *   rendered header ending with a newline
    */
  override def generateLicense(raw: String): String =
    val body = raw.linesIterator.mkString("\n")
    s"<!--\n$body\n-->\n"
