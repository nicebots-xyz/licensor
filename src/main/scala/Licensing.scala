// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

/** Result of checking whether a license header is present in a file. */
enum LicenseState:
  case Missing
  case Present
  case External

/** Defines how to render and apply license headers for a file type. */
trait FileHandler:
  /** File extensions (without dots) supported by this handler. */
  def extensions: Set[String]

  /** Render the raw license text into a comment header for this file type.
    *
    * @param raw
    *   plain-text license content
    * @return
    *   license header formatted for this file type
    */
  def generateLicense(raw: String): String

  /** Add a license header if missing.
    *
    * @param fileDump
    *   full file contents
    * @param licenseText
    *   plain-text license content
    * @return
    *   tuple of updated contents and whether a header was added
    */
  def addLicense(fileDump: String, licenseText: String): (String, Boolean) =
    val rendered = generateLicense(licenseText)
    if checkLicense(fileDump, licenseText) == LicenseState.Missing then (rendered + fileDump, true)
    else (fileDump, false)

  /** Check whether the file starts with the rendered license header.
    *
    * @param fileDump
    *   full file contents
    * @param licenseText
    *   plain-text license content
    * @return
    *   true if the rendered header appears at the beginning of the file
    */
  private def hasLicense(fileDump: String, licenseText: String): Boolean =
    val expectedFirst = generateLicense(licenseText).linesIterator.nextOption.getOrElse("")
    val actualFirst   = fileDump.linesIterator.nextOption.getOrElse("")
    actualFirst == expectedFirst

  /** Detect another license header by scanning for external markers.
    *
    * @param fileDump
    *   full file contents
    * @param externalMarkers
    *   markers that indicate a different license header is present
    * @param scanLines
    *   number of lines at the top of the file to scan
    * @return
    *   true if an external header is detected
    */
  private def hasExternal(
      fileDump: String,
      externalMarkers: Vector[String] = Vector("Copyright", "SPDX-License-Identifier"),
      scanLines: Int = 3
  ): Boolean =
    val headerLines = fileDump.linesIterator.take(scanLines).map(_.trim).toVector
    externalMarkers.exists(marker => headerLines.exists(_.contains(marker)))

  /** Determine the license state for the file.
    *
    * @param fileDump
    *   full file contents
    * @param licenseText
    *   plain-text license content
    * @return
    *   license state based on header detection
    */
  def checkLicense(
      fileDump: String,
      licenseText: String
  ): LicenseState =
    if hasLicense(fileDump, licenseText) then LicenseState.Present
    else if hasExternal(fileDump) then LicenseState.External
    else LicenseState.Missing
