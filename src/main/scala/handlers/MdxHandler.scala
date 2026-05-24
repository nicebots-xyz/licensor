// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots
package handlers

/** Handles .mdx files with frontmatter-aware JSX comment headers. */
class MdxHandler extends FrontmatterHandler:
  override def extensions: Set[String] = Set("mdx")

  override def generateLicense(raw: String): String =
    val body = raw.linesIterator.mkString("\n")
    s"{/*\n$body\n*/}\n"
