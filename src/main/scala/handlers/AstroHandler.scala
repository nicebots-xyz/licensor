// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots
package handlers

/** Handles .astro files with frontmatter-aware HTML comment headers. */
class AstroHandler extends FrontmatterHandler:
  override def extensions: Set[String] = Set("astro")

  override def generateLicense(raw: String): String =
    val body = raw.linesIterator.mkString("\n")
    s"<!--\n$body\n-->\n"
