// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots
package handlers

import xyz.nicebots.{FileHandler, LicenseState, LineParsing}

/** Base handler for formats that place license headers after optional YAML frontmatter. */
abstract class FrontmatterHandler extends FileHandler:

  override def addLicense(fileDump: String, licenseText: String): (String, Boolean) =
    val rendered = generateLicense(licenseText)
    if checkLicense(fileDump, licenseText) == LicenseState.Missing then
      (insertAfterFrontmatter(fileDump, rendered), true)
    else (fileDump, false)

  override def checkLicense(fileDump: String, licenseText: String): LicenseState =
    val rendered = generateLicense(licenseText)
    val lines    = LineParsing.splitLines(fileDump)
    val insertAt = LineParsing.frontmatterInsertIndex(lines)
    if hasRenderedHeader(lines, insertAt, rendered) then LicenseState.Present
    else if LineParsing.hasExternalAt(lines, insertAt) then LicenseState.External
    else LicenseState.Missing

  private def insertAfterFrontmatter(fileDump: String, rendered: String): String =
    val lines           = LineParsing.splitLines(fileDump)
    val insertAt        = LineParsing.frontmatterInsertIndex(lines)
    val trimmedInsertAt = LineParsing.skipBlankLines(lines, insertAt)
    val headerLines     = LineParsing.splitLines(rendered.stripSuffix("\n"))
    val separator       = if insertAt > 0 then Vector("") else Vector.empty
    val updatedLines    =
      lines.patch(insertAt, separator ++ headerLines, trimmedInsertAt - insertAt)
    updatedLines.mkString("\n")

  private def hasRenderedHeader(
      lines: Vector[String],
      insertAt: Int,
      rendered: String
  ): Boolean =
    val contentStart = LineParsing.skipBlankLines(lines, insertAt)
    val headerLines  = LineParsing.splitLines(rendered.stripSuffix("\n"))
    val fileHeader   = lines.slice(contentStart, contentStart + headerLines.length)
    fileHeader == headerLines
