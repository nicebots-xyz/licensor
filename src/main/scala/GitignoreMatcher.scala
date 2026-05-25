// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** Gitignore pattern matching for directory walks.
  *
  * Loads `.gitignore` files while descending the tree. Patterns from each ancestor directory apply,
  * and the last matching rule wins (including negated rules).
  */
final class GitignoreMatcher private (root: os.Path):

  private val rulesByDir =
    scala.collection.mutable.Map.empty[os.Path, Vector[GitignoreMatcher.Rule]]

  def enterDirectory(dir: os.Path): Unit =
    val gitignore = dir / ".gitignore"
    if os.isFile(gitignore) then
      rulesByDir(dir) = GitignoreMatcher.parseRules(
        Files.readString(gitignore.toNIO, StandardCharsets.UTF_8)
      )

  def isIgnored(path: os.Path, isDirectory: Boolean): Boolean =
    val segments =
      try path.relativeTo(root).segments
      catch case _: Throwable => Vector.empty[String]

    var ignored = false
    var current = root

    def applyDirRules(dir: os.Path): Unit =
      rulesByDir.get(dir).foreach { rules =>
        val relativeToDir =
          try GitignoreMatcher.normalizeRelative(path.relativeTo(dir).toString)
          catch case _: Throwable => return
        rules.foreach { rule =>
          if GitignoreMatcher.ruleMatches(rule, relativeToDir, isDirectory) then
            ignored = !rule.negated
        }
      }

    applyDirRules(root)
    segments.foreach { seg =>
      current = current / seg
      applyDirRules(current)
    }
    ignored

object GitignoreMatcher:

  private case class Rule(
      pattern: String,
      negated: Boolean,
      directoryOnly: Boolean,
      anchored: Boolean
  )

  def apply(root: os.Path): GitignoreMatcher =
    val matcher = new GitignoreMatcher(root)
    matcher.enterDirectory(root)
    matcher

  private def normalizeRelative(path: String): String =
    path.replace('\\', '/')

  private def parseRules(content: String): Vector[Rule] =
    content.linesIterator
      .map(_.trim)
      .filter(line => line.nonEmpty && !line.startsWith("#"))
      .flatMap(parseLine)
      .toVector

  private def parseLine(line: String): Option[Rule] =
    var text    = line
    val negated = text.startsWith("!")
    if negated then text = text.drop(1)
    val dirOnly = text.endsWith("/")
    if dirOnly then text = text.dropRight(1)
    val anchored = text.startsWith("/")
    if anchored then text = text.drop(1)
    if text.isEmpty then None
    else Some(Rule(text, negated, dirOnly, anchored))

  private def ruleMatches(rule: Rule, relativePath: String, isDirectory: Boolean): Boolean =
    if rule.directoryOnly then
      val dir = rule.pattern
      relativePath == dir || relativePath.startsWith(dir + "/") ||
      relativePath.endsWith("/" + dir) || relativePath.contains("/" + dir + "/")
    else
      val target =
        if rule.anchored then relativePath
        else if rule.pattern.contains('/') then relativePath
        else relativePath.substring(relativePath.lastIndexOf('/') + 1)
      pathMatches(rule.pattern, target)

  private def pathMatches(pattern: String, path: String): Boolean =
    path.matches(gitignorePatternToRegex(pattern))

  private def gitignorePatternToRegex(pattern: String): String =
    val sb = new StringBuilder("^")
    var i  = 0
    while i < pattern.length do
      pattern(i) match
        case '*' =>
          if i + 1 < pattern.length && pattern(i + 1) == '*' then
            sb.append(".*")
            i += 2
            if i < pattern.length && pattern(i) == '/' then i += 1
          else
            sb.append("[^/]*")
            i += 1
        case '?' =>
          sb.append("[^/]")
          i += 1
        case c if ".[]{}()+^$|\\".contains(c) =>
          sb.append('\\').append(c)
          i += 1
        case c =>
          sb.append(c)
          i += 1
    sb.append('$').result()
