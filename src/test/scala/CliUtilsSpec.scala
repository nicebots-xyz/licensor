// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Files

class CliUtilsSpec extends AnyFunSuite:
  test("ignore pattern matching") {
    val tempDir   = Files.createTempDirectory("test")
    val sourceDir = tempDir.resolve("source")
    val vendorDir = sourceDir.resolve("vendor")
    val appDir    = sourceDir.resolve("app")

    Files.createDirectories(vendorDir)
    Files.createDirectories(appDir)
    Files.writeString(vendorDir.resolve("third.py"), "content")
    Files.writeString(appDir.resolve("main.py"), "content")

    val baseDir     = os.Path(tempDir)
    val globs       = Vector("source/**/*.py")
    val ignoreGlobs = Vector("source/vendor/**")

    val result        = CliUtils.collectFiles(globs, ignoreGlobs, baseDir)
    val relativePaths = result.map(_.relativeTo(baseDir).toString).sorted

    assert(relativePaths.contains("source/app/main.py"), "Should include main.py")
    assert(!relativePaths.contains("source/vendor/third.py"), "Should NOT include vendor/third.py")
  }

  test("directory path should recursively collect all files") {
    val tempDir = Files.createTempDirectory("test")
    val srcDir  = tempDir.resolve("src")
    val subDir  = srcDir.resolve("subdir")

    Files.createDirectories(subDir)
    Files.writeString(srcDir.resolve("file1.py"), "content")
    Files.writeString(subDir.resolve("file2.py"), "content")

    val baseDir       = os.Path(tempDir)
    val result        = CliUtils.collectFiles(Vector("src"), Vector.empty, baseDir)
    val relativePaths = result.map(_.relativeTo(baseDir).toString).sorted

    assert(relativePaths.contains("src/file1.py"), "Should include file1.py")
    assert(relativePaths.contains("src/subdir/file2.py"), "Should include file2.py in subdirectory")
  }

  test("current directory path '.' should collect all files recursively") {
    val tempDir = Files.createTempDirectory("test")
    val srcDir  = tempDir.resolve("src")

    Files.createDirectories(srcDir)
    Files.writeString(tempDir.resolve("root.py"), "content")
    Files.writeString(srcDir.resolve("nested.py"), "content")

    val baseDir       = os.Path(tempDir)
    val result        = CliUtils.collectFiles(Vector("."), Vector.empty, baseDir)
    val relativePaths = result.map(_.relativeTo(baseDir).toString).sorted

    assert(relativePaths.contains("root.py"), "Should include root.py")
    assert(relativePaths.contains("src/nested.py"), "Should include nested.py")
  }

  test("glob pattern with ./ prefix should be normalized") {
    val tempDir = Files.createTempDirectory("test")
    val srcDir  = tempDir.resolve("src")

    Files.createDirectories(srcDir)
    Files.writeString(srcDir.resolve("file.py"), "content")

    val baseDir       = os.Path(tempDir)
    val result        = CliUtils.collectFiles(Vector("./**/*.py"), Vector.empty, baseDir)
    val relativePaths = result.map(_.relativeTo(baseDir).toString).sorted

    assert(relativePaths.contains("src/file.py"), "Should match files with normalized glob pattern")
  }

  test("ignore pattern with ** should exclude directory contents") {
    val tempDir    = Files.createTempDirectory("test")
    val skippedDir = tempDir.resolve("skipped")
    val srcDir     = tempDir.resolve("src")

    Files.createDirectories(skippedDir)
    Files.createDirectories(srcDir)
    Files.writeString(skippedDir.resolve("test.py"), "content")
    Files.writeString(srcDir.resolve("main.py"), "content")

    val baseDir       = os.Path(tempDir)
    val result        = CliUtils.collectFiles(Vector("**/*.py"), Vector("skipped/**"), baseDir)
    val relativePaths = result.map(_.relativeTo(baseDir).toString).sorted

    assert(relativePaths.contains("src/main.py"), "Should include src/main.py")
    assert(!relativePaths.contains("skipped/test.py"), "Should exclude skipped/test.py")
  }

  test("ignore pattern with backslashes should work on Windows") {
    val tempDir    = Files.createTempDirectory("test")
    val skippedDir = tempDir.resolve("skipped")
    val srcDir     = tempDir.resolve("src")

    Files.createDirectories(skippedDir)
    Files.createDirectories(srcDir)
    Files.writeString(skippedDir.resolve("test.py"), "content")
    Files.writeString(srcDir.resolve("main.py"), "content")

    val baseDir       = os.Path(tempDir)
    val result        = CliUtils.collectFiles(Vector("**/*.py"), Vector("skipped\\**"), baseDir)
    val relativePaths = result.map(_.relativeTo(baseDir).toString).sorted

    assert(relativePaths.contains("src/main.py"), "Should include src/main.py")
    assert(
      !relativePaths.contains("skipped/test.py"),
      "Should exclude skipped/test.py with backslash pattern"
    )
  }

  test("multiple ignore patterns should all be applied") {
    val tempDir   = Files.createTempDirectory("test")
    val vendorDir = tempDir.resolve("vendor")
    val buildDir  = tempDir.resolve("build")
    val srcDir    = tempDir.resolve("src")

    Files.createDirectories(vendorDir)
    Files.createDirectories(buildDir)
    Files.createDirectories(srcDir)
    Files.writeString(vendorDir.resolve("lib.py"), "content")
    Files.writeString(buildDir.resolve("output.py"), "content")
    Files.writeString(srcDir.resolve("main.py"), "content")

    val baseDir = os.Path(tempDir)
    val result  = CliUtils.collectFiles(Vector("**/*.py"), Vector("vendor/**", "build/**"), baseDir)
    val relativePaths = result.map(_.relativeTo(baseDir).toString).sorted

    assert(relativePaths.contains("src/main.py"), "Should include src/main.py")
    assert(!relativePaths.contains("vendor/lib.py"), "Should exclude vendor/lib.py")
    assert(!relativePaths.contains("build/output.py"), "Should exclude build/output.py")
  }

  test("combining directory path and glob patterns") {
    val tempDir = Files.createTempDirectory("test")
    val srcDir  = tempDir.resolve("src")
    val docsDir = tempDir.resolve("docs")

    Files.createDirectories(srcDir)
    Files.createDirectories(docsDir)
    Files.writeString(srcDir.resolve("main.py"), "content")
    Files.writeString(docsDir.resolve("README.md"), "content")
    Files.writeString(tempDir.resolve("test.txt"), "content")

    val baseDir       = os.Path(tempDir)
    val result        = CliUtils.collectFiles(Vector("src", "**/*.md"), Vector.empty, baseDir)
    val relativePaths = result.map(_.relativeTo(baseDir).toString).sorted

    assert(relativePaths.contains("src/main.py"), "Should include files from directory path")
    assert(relativePaths.contains("docs/README.md"), "Should include files matching glob")
    assert(!relativePaths.contains("test.txt"), "Should not include unmatched files")
  }
