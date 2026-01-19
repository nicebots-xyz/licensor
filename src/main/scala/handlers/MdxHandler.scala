// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots
package handlers

import xyz.nicebots.FileHandler
import xyz.nicebots.LicenseState

/** Handles .mdx files with frontmatter-aware JSX comment headers. */
class MdxHandler extends FileHandler:
  /** Supported file extensions.
    *
    * @return
    *   extension set without dots
    */
  override def extensions: Set[String] =
    Set(
      "mdx"
    )

  /** Render a JSX comment header.
    *
    * @param raw
    *   plain-text license content
    * @return
    *   rendered header ending with a newline
    */
  override def generateLicense(raw: String): String =
    val body = raw.linesIterator.mkString("\n")
    s"{/*\n$body\n*/}\n"

  /** Add a license header after frontmatter if present. */
  override def addLicense(fileDump: String, licenseText: String): (String, Boolean) =
    val rendered = generateLicense(licenseText)
    if checkLicense(fileDump, licenseText) == LicenseState.Missing then
      (insertAfterFrontmatter(fileDump, rendered), true)
    else (fileDump, false)

  /** Check for a license header after frontmatter if present. */
  override def checkLicense(fileDump: String, licenseText: String): LicenseState =
    val rendered      = generateLicense(licenseText)
    val lines         = splitLines(fileDump)
    val insertAt      = frontmatterInsertIndex(lines)
    val expectedFirst = rendered.linesIterator.nextOption.getOrElse("")
    val actualFirst   = lines.drop(insertAt).dropWhile(_.trim.isEmpty).headOption.getOrElse("")
    if expectedFirst == actualFirst then LicenseState.Present
    else if hasExternalAt(lines, insertAt) then LicenseState.External
    else LicenseState.Missing

  private def insertAfterFrontmatter(fileDump: String, rendered: String): String =
    val lines           = splitLines(fileDump)
    val insertAt        = frontmatterInsertIndex(lines)
    val trimmedInsertAt = skipBlankLines(lines, insertAt)
    val headerLines     = splitLines(rendered.stripSuffix("\n"))
    val separator       = if insertAt > 0 then Vector("") else Vector.empty
    val updatedLines    =
      lines.patch(insertAt, separator ++ headerLines, trimmedInsertAt - insertAt)
    updatedLines.mkString("\n")

  private def frontmatterInsertIndex(lines: Vector[String]): Int =
    val markerIndexes = lines.zipWithIndex.collect {
      case (line, idx) if line.trim.startsWith("---") => idx
    }
    if markerIndexes.length >= 2 then markerIndexes(1) + 1 else 0

  private def hasExternalAt(
      lines: Vector[String],
      insertAt: Int,
      externalMarkers: Vector[String] = Vector("Copyright", "SPDX-License-Identifier"),
      scanLines: Int = 3
  ): Boolean =
    val headerLines =
      lines.drop(insertAt).dropWhile(_.trim.isEmpty).take(scanLines).map(_.trim)
    externalMarkers.exists(marker => headerLines.exists(_.contains(marker)))

  private def splitLines(text: String): Vector[String] =
    text
      .split("\n", -1)
      .toVector
      .map(line => if line.endsWith("\r") then line.dropRight(1) else line)

  private def skipBlankLines(lines: Vector[String], startAt: Int): Int =
    var idx = startAt
    while idx < lines.length && lines(idx).trim.isEmpty do idx += 1
    idx
