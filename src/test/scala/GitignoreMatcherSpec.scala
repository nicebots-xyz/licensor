// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Files

class GitignoreMatcherSpec extends AnyFunSuite:
  test("root gitignore excludes directory and its files") {
    val tempDir = Files.createTempDirectory("gitignore")
    Files.writeString(tempDir.resolve(".gitignore"), "node_modules/\n")
    Files.createDirectories(tempDir.resolve("node_modules/pkg"))
    Files.writeString(tempDir.resolve("node_modules/pkg/index.js"), "js")
    Files.createDirectories(tempDir.resolve("src"))
    Files.writeString(tempDir.resolve("src/main.py"), "py")

    val baseDir = os.Path(tempDir)
    val result  = CliUtils.collectFiles(Vector("**/*"), Vector.empty, baseDir)

    val paths = result.map(_.relativeTo(baseDir).toString)
    assert(paths.contains("src/main.py"))
    assert(!paths.exists(_.contains("node_modules")))
  }

  test("no-respect-gitignore collects gitignored paths") {
    val tempDir = Files.createTempDirectory("gitignore")
    Files.writeString(tempDir.resolve(".gitignore"), "skipped/\n")
    Files.createDirectories(tempDir.resolve("skipped"))
    Files.writeString(tempDir.resolve("skipped/hidden.py"), "py")
    Files.writeString(tempDir.resolve("visible.py"), "py")

    val baseDir = os.Path(tempDir)
    val result  =
      CliUtils.collectFiles(Vector("*.py", "**/*.py"), Vector.empty, baseDir, respectGitignore = false)
    val paths = result.map(_.relativeTo(baseDir).toString).sorted

    assert(paths.contains("skipped/hidden.py"))
    assert(paths.contains("visible.py"))
  }

  test("config ignores merge with CLI ignore globs") {
    val tempDir = Files.createTempDirectory("gitignore")
    Files.createDirectories(tempDir.resolve("vendor"))
    Files.createDirectories(tempDir.resolve("build"))
    Files.createDirectories(tempDir.resolve("src"))
    Files.writeString(tempDir.resolve("vendor/lib.py"), "py")
    Files.writeString(tempDir.resolve("build/out.py"), "py")
    Files.createDirectories(tempDir.resolve("src"))
    Files.writeString(tempDir.resolve("src/main.py"), "py")

    val baseDir = os.Path(tempDir)
    val result  = CliUtils.collectFiles(Vector("*.py", "**/*.py"), Vector("build/**"), baseDir)

    val paths = result.map(_.relativeTo(baseDir).toString)
    assert(paths.contains("src/main.py"))
    assert(paths.contains("vendor/lib.py"))
    assert(!paths.contains("build/out.py"))
  }
