// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

/** Shared line-based parsing helpers for license header placement. */
object LineParsing:

  /** Split text into lines, preserving trailing empty segments and normalizing CRLF. */
  def splitLines(text: String): Vector[String] =
    text
      .split("\n", -1)
      .toVector
      .map(line => if line.endsWith("\r") then line.dropRight(1) else line)

  /** Index immediately after a YAML-style `---` frontmatter block, if present. */
  def frontmatterInsertIndex(lines: Vector[String]): Int =
    val markerIndexes = lines.zipWithIndex.collect {
      case (line, idx) if line.trim == "---" || line.trim.startsWith("---") => idx
    }
    if markerIndexes.length >= 2 then markerIndexes(1) + 1 else 0

  /** Advance `startAt` past consecutive blank lines. */
  def skipBlankLines(lines: Vector[String], startAt: Int): Int =
    var idx = startAt
    while idx < lines.length && lines(idx).trim.isEmpty do idx += 1
    idx

  /** Detect external license markers in the first `scanLines` non-blank lines after `insertAt`. */
  def hasExternalAt(
      lines: Vector[String],
      insertAt: Int,
      externalMarkers: Vector[String] = Vector("Copyright", "SPDX-License-Identifier"),
      scanLines: Int = 5
  ): Boolean =
    val headerLines =
      lines.drop(insertAt).dropWhile(_.trim.isEmpty).take(scanLines).map(_.trim)
    externalMarkers.exists(marker => headerLines.exists(_.contains(marker)))
