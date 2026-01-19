// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots
package config

import org.yaml.snakeyaml.Yaml

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Year
import java.util.{List => JList}
import java.util.{Map => JMap}

/** Copyright year specification used for notice generation. */
enum YearRange:
  case Single(year: Int)
  case Range(start: Int, end: Int)

/** Parsed licensing configuration.
  *
  * @param spdxId
  *   SPDX identifier, if provided
  * @param holder
  *   copyright holder
  * @param years
  *   copyright year or year range
  */
case class LicensingConfig(
    spdxId: Option[String],
    holder: String,
    years: YearRange
)

object LicensingConfig:
  /** Load and parse a YAML config file.
    *
    * @param path
    *   path to the YAML config
    * @return
    *   parsed config or an error message
    */
  def load(path: Path): Either[String, LicensingConfig] =
    val content = Files.readString(path, StandardCharsets.UTF_8)
    parse(content)

  /** Parse YAML text into a config.
    *
    * @param yamlText
    *   YAML document content
    * @return
    *   parsed config or an error message
    */
  def parse(yamlText: String): Either[String, LicensingConfig] =
    val yaml                = Yaml()
    val data: Object | Null = yaml.load(yamlText)
    data match
      case map: JMap[?, ?] =>
        parseMap(map.asInstanceOf[JMap[String, Object]])
      case null =>
        Left("Config file is empty")
      case other =>
        Left(s"Expected YAML mapping at top level, got: ${other.getClass.getName}")

  /** Build the raw license text from config values.
    *
    * @param config
    *   parsed licensing config
    * @return
    *   plain-text license content
    */
  def toLicenseText(config: LicensingConfig): String =
    val yearText = config.years match
      case YearRange.Single(year)      => year.toString
      case YearRange.Range(start, end) => s"$start-$end"

    val spdxLine = config.spdxId match
      case Some(id) => s"SPDX-License-Identifier: $id"
      case None     => "All rights reserved"

    s"$spdxLine\nCopyright: $yearText ${config.holder}"

  /** Parse top-level YAML fields into a config.
    *
    * @param values
    *   top-level YAML mapping
    * @return
    *   parsed config or an error message
    */
  private def parseMap(values: JMap[String, Object]): Either[String, LicensingConfig] =
    val holder = values.get("holder") match
      case s: String if s.trim.nonEmpty => Right(s.trim)
      case _                            => Left("Missing or empty 'holder' in config")

    val spdxId = values.get("spdx") match
      case s: String if s.trim.nonEmpty => Some(s.trim)
      case _                            => None

    val yearValue = values.get("year")
    val years     = parseYears(yearValue)

    for
      h <- holder
      y <- years
    yield LicensingConfig(spdxId = spdxId, holder = h, years = y)

  /** Parse the optional year value into a YearRange.
    *
    * @param value
    *   YAML value for the year field
    * @return
    *   parsed YearRange or an error message
    */
  private def parseYears(value: Object): Either[String, YearRange] =
    if value == null then Right(YearRange.Single(Year.now.getValue))
    else
      value match
        case n: Number =>
          Right(YearRange.Single(n.intValue))
        case list: JList[?] =>
          val items = list.toArray.toVector
          if items.length != 2 then Left("Expected 'year' array with exactly two numbers")
          else
            (items(0), items(1)) match
              case (start: Number, end: Number) =>
                Right(YearRange.Range(start.intValue, end.intValue))
              case _ =>
                Left("Expected 'year' array with two numbers")
        case _ =>
          Left("Expected 'year' to be a number or an array of two numbers")
