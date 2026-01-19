// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

import org.slf4j.Logger

import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

import config.LicensingConfig

/** Utility helpers for CLI file handling and config loading.
  *
  * Provides cross-platform file collection using glob patterns and configuration loading with error
  * handling. Uses `os-lib` for path normalization and Java NIO's PathMatcher for glob matching.
  *
  * ==Supported Glob Patterns==
  * Patterns use Java NIO glob syntax:
  *   - `*` matches any characters within a single path segment (doesn't cross `/`)
  *   - `**` matches any characters across multiple path segments
  *   - `?` matches exactly one character
  *   - `[abc]` matches one character from the set
  *   - `{a,b}` matches either pattern a or b
  *
  * All patterns are matched against paths normalized with forward slashes.
  */
object CliUtils {

  /** Collect input files based on glob patterns and ignore globs.
    *
    * @param globs
    *   input glob patterns or direct file paths
    * @param ignoreGlobs
    *   glob patterns to exclude from results
    * @param baseDir
    *   base directory for resolving relative paths and walking for glob matches
    * @return
    *   distinct matching files that are not excluded by ignore patterns
    */
  def collectFiles(
      globs: Vector[String],
      ignoreGlobs: Vector[String],
      baseDir: os.Path
  ): Vector[os.Path] =
    val logger = org.slf4j.LoggerFactory.getLogger("xyz.nicebots.CliUtils")
    logger.debug(s"Base directory: $baseDir")
    logger.debug(s"Input globs: ${globs.mkString(", ")}")
    logger.debug(s"Ignore globs: ${ignoreGlobs.mkString(", ")}")

    val candidates = expandPatterns(globs, baseDir)
    logger.debug(s"Candidates: ${candidates.map(_.relativeTo(baseDir)).mkString(", ")}")

    if ignoreGlobs.isEmpty then candidates
    else
      val ignored = expandPatterns(ignoreGlobs, baseDir).toSet
      val result  = candidates.filterNot(ignored.contains)
      logger.debug(s"After ignores: ${result.map(_.relativeTo(baseDir)).mkString(", ")}")
      result

  /** Expand a list of patterns (globs or paths) into matching files.
    *
    * @param patterns
    *   glob patterns or direct file/directory paths
    * @param baseDir
    *   base directory for resolving relative paths and walking
    * @return
    *   all files matching the patterns
    */
  private def expandPatterns(patterns: Vector[String], baseDir: os.Path): Vector[os.Path] =
    val normalized = patterns.map { pattern =>
      val withoutPrefix = if pattern.startsWith("./") then pattern.drop(2) else pattern
      withoutPrefix.replace('\\', '/')
    }

    val (globPatterns, directPaths) = normalized.partition(isGlobPattern)

    val directMatches = directPaths.flatMap { p =>
      val resolved = resolvePath(p, baseDir)
      if os.isFile(resolved) then Vector(resolved)
      else if os.isDir(resolved) then os.walk(resolved).filter(os.isFile).toVector
      else Vector.empty
    }

    val globMatchers = globPatterns.map(createPathMatcher)
    val globMatches  =
      if globMatchers.isEmpty then Vector.empty
      else
        os.walk(baseDir)
          .filter { path =>
            os.isFile(path) && matchesAny(baseDir, path, globMatchers)
          }
          .toVector

    (directMatches ++ globMatches).distinct

  /** Load configuration from a file, exiting the process on error.
    *
    * Resolves the config path relative to `baseDir` if not absolute, then attempts to parse it as a
    * [[LicensingConfig]]. Logs an error and exits with code 1 if the file is missing or malformed.
    *
    * @param path
    *   config file path (absolute or relative to `baseDir`)
    * @param baseDir
    *   base directory for resolving relative paths
    * @param logger
    *   logger used for error messages
    * @return
    *   parsed licensing config (never returns on error, calls `sys.exit(1)`)
    */
  def loadConfigOrExit(path: String, baseDir: os.Path, logger: Logger): LicensingConfig =
    val configPath = resolvePath(path, baseDir)
    if !os.isFile(configPath) then
      logger.error(s"Config file not found: $path")
      sys.exit(1)
    LicensingConfig.load(configPath.toNIO) match
      case Left(error) =>
        logger.error(s"Config error: $error")
        sys.exit(1)
      case Right(cfg) => cfg

  /** Resolve a path string relative to a base directory.
    *
    * @param path
    *   input path string (absolute or relative)
    * @param baseDir
    *   base directory for resolving relative paths
    * @return
    *   resolved absolute path
    */
  private def resolvePath(path: String, baseDir: os.Path): os.Path =
    val parsed = os.FilePath(path)
    parsed match
      case p: os.Path    => p
      case p: os.RelPath => baseDir / p
      case p: os.SubPath => baseDir / p

  /** Create a PathMatcher from a glob pattern.
    *
    * @param pattern
    *   glob pattern using Java NIO glob syntax
    * @return
    *   compiled PathMatcher
    */
  private def createPathMatcher(pattern: String): PathMatcher =
    FileSystems.getDefault.getPathMatcher(s"glob:$pattern")

  /** Check if a path matches any of the given patterns.
    *
    * Path is normalized to forward slashes via os-lib before matching.
    *
    * @param base
    *   base directory for relativization
    * @param path
    *   absolute path to check
    * @param matchers
    *   compiled PathMatchers
    * @return
    *   true if the path matches any pattern
    */
  private def matchesAny(
      base: os.Path,
      path: os.Path,
      matchers: Vector[PathMatcher]
  ): Boolean =
    val relPathStr = path.relativeTo(base).toString
    val relPath    = Paths.get(relPathStr)
    val result     = matchers.exists(_.matches(relPath))
    if result then
      org.slf4j.LoggerFactory
        .getLogger("xyz.nicebots.CliUtils")
        .debug(s"Path '$relPathStr' matched by a pattern")
    result

  private def isGlobPattern(s: String): Boolean =
    s.exists(c => c == '*' || c == '?' || c == '[' || c == '{')
}
