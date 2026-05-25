// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

import org.slf4j.Logger

import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

import config.LicensingConfig

/** Utility helpers for CLI file handling and config loading. */
object CliUtils:

  /** Collect input files based on glob patterns and ignore rules.
    *
    * Ignore rules are merged from the config file, CLI flags, and (by default) `.gitignore` files
    * encountered while walking.
    */
  def collectFiles(
      globs: Vector[String],
      ignoreGlobs: Vector[String],
      baseDir: os.Path,
      respectGitignore: Boolean = true
  ): Vector[os.Path] =
    val logger = org.slf4j.LoggerFactory.getLogger("xyz.nicebots.CliUtils")
    logger.debug(s"Base directory: $baseDir")
    logger.debug(s"Input globs: ${globs.mkString(", ")}")
    logger.debug(s"Ignore globs: ${ignoreGlobs.mkString(", ")}")
    logger.debug(s"Respect gitignore: $respectGitignore")

    val ignoreMatchers = ignoreGlobs.map(normalizePattern).map(createPathMatcher)
    val candidates     = expandPatterns(globs, baseDir, respectGitignore)
    val result         =
      if ignoreMatchers.isEmpty then candidates
      else candidates.filterNot(path => matchesAny(baseDir, path, ignoreMatchers))

    logger.debug(s"Matched files: ${result.map(_.relativeTo(baseDir)).mkString(", ")}")
    result

  private def expandPatterns(
      patterns: Vector[String],
      baseDir: os.Path,
      respectGitignore: Boolean
  ): Vector[os.Path] =
    val normalized                  = patterns.map(normalizePattern)
    val (globPatterns, directPaths) = normalized.partition(isGlobPattern)

    val directMatches = directPaths.flatMap { p =>
      FileWalker.listFiles(
        resolvePath(p, baseDir),
        respectGitignore = respectGitignore,
        gitignoreRoot = Some(baseDir)
      )
    }

    val globMatchers = globPatterns.map(createPathMatcher)
    val globMatches  =
      if globMatchers.isEmpty then Vector.empty
      else
        val extensionHints = FileWalker.extensionHintsFromGlobs(globPatterns)
        FileWalker
          .listFiles(
            baseDir,
            extensionHints,
            respectGitignore,
            gitignoreRoot = Some(baseDir)
          )
          .filter(path => matchesAny(baseDir, path, globMatchers))

    (directMatches ++ globMatches).distinct

  def loadConfigOrExit(path: String, baseDir: os.Path, logger: Logger): LicensingConfig =
    val configPath = resolvePath(path, baseDir)
    if !os.isFile(configPath) then CliUx.fatal(s"Config file not found: $path")
    LicensingConfig.load(configPath.toNIO) match
      case Left(error) =>
        CliUx.fatal(s"Config error: $error")
      case Right(cfg) => cfg

  private def resolvePath(path: String, baseDir: os.Path): os.Path =
    os.FilePath(path) match
      case p: os.Path    => p
      case p: os.RelPath => baseDir / p
      case p: os.SubPath => baseDir / p

  private def normalizePattern(pattern: String): String =
    val withoutPrefix = if pattern.startsWith("./") then pattern.drop(2) else pattern
    withoutPrefix.replace('\\', '/')

  private def createPathMatcher(pattern: String): PathMatcher =
    FileSystems.getDefault.getPathMatcher(s"glob:$pattern")

  private def matchesAny(
      base: os.Path,
      path: os.Path,
      matchers: Vector[PathMatcher]
  ): Boolean =
    matchers.exists(_.matches(Paths.get(path.relativeTo(base).toString)))

  private def isGlobPattern(s: String): Boolean =
    s.exists(c => c == '*' || c == '?' || c == '[' || c == '{')
