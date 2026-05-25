// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

import org.scalatest.funsuite.AnyFunSuite

import config.LicensingConfig
import config.YearRange

class LicensingConfigSpec extends AnyFunSuite:
  test("parse ignores list from YAML config") {
    val yaml =
      """holder: Example Corp
        |spdx: MIT
        |year: 2026
        |ignores:
        |  - vendor/**
        |  - build/**
        |""".stripMargin

    LicensingConfig.parse(yaml) match
      case Right(config) =>
        assert(config.holder == "Example Corp")
        assert(config.spdxId.contains("MIT"))
        assert(config.years == YearRange.Single(2026))
        assert(config.ignores == Vector("vendor/**", "build/**"))
      case Left(err) => fail(err)
  }

  test("missing ignores field defaults to empty vector") {
    val yaml =
      """holder: Example Corp
        |year: 2026
        |""".stripMargin

    LicensingConfig.parse(yaml) match
      case Right(config) => assert(config.ignores.isEmpty)
      case Left(err)     => fail(err)
  }

  test("invalid ignores field returns error") {
    val yaml =
      """holder: Example Corp
        |year: 2026
        |ignores: vendor/**
        |""".stripMargin

    LicensingConfig.parse(yaml) match
      case Right(_)  => fail("Expected parse error for non-list ignores")
      case Left(err) => assert(err.contains("ignores"))
  }
