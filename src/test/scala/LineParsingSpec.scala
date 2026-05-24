// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

import org.scalatest.funsuite.AnyFunSuite

class LineParsingSpec extends AnyFunSuite:
  test("frontmatterInsertIndex finds closing marker") {
    val lines = Vector("---", "title: x", "---", "", "body")
    assert(LineParsing.frontmatterInsertIndex(lines) == 3)
  }

  test("hasExternalAt detects SPDX after frontmatter") {
    val lines = Vector("---", "a: 1", "---", "", "SPDX-License-Identifier: Apache-2.0")
    assert(LineParsing.hasExternalAt(lines, LineParsing.frontmatterInsertIndex(lines)))
  }
