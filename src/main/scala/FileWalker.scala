// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

/** Directory traversal for CLI file collection.
  *
  * By default respects `.gitignore` files discovered while walking. Symbolic links are never
  * followed.
  */
object FileWalker:

  def listFiles(
      root: os.Path,
      extensionHints: Set[String] = Set.empty,
      respectGitignore: Boolean = true
  ): Vector[os.Path] =
    if !os.exists(root) then Vector.empty
    else if os.isFile(root) then Vector(root)
    else
      val gitignore = if respectGitignore then Some(GitignoreMatcher(root)) else None
      val result    = Vector.newBuilder[os.Path]
      val visitor   = new SimpleFileVisitor[Path]:
        override def preVisitDirectory(
            dir: Path,
            attrs: BasicFileAttributes
        ): FileVisitResult =
          if attrs.isSymbolicLink then FileVisitResult.SKIP_SUBTREE
          else
            val path = os.Path(dir)
            gitignore.foreach(_.enterDirectory(path))
            if gitignore.exists(_.isIgnored(path, isDirectory = true)) then
              FileVisitResult.SKIP_SUBTREE
            else super.preVisitDirectory(dir, attrs)

        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult =
          if attrs.isRegularFile && !attrs.isSymbolicLink then
            val path    = os.Path(file)
            val ignored = gitignore.exists(_.isIgnored(path, isDirectory = false))
            val extOk   = extensionHints.isEmpty || matchesExtensionHint(path, extensionHints)
            if !ignored && extOk then result += path
          FileVisitResult.CONTINUE

        override def visitFileFailed(file: Path, exc: IOException): FileVisitResult =
          FileVisitResult.CONTINUE

      try Files.walkFileTree(root.toNIO, visitor)
      catch case _: IOException => ()
      result.result()

  def extensionHintsFromGlobs(globs: Vector[String]): Set[String] =
    globs.flatMap(extensionFromGlob).map(_.toLowerCase).toSet

  private val globExtensionPattern = ".*\\*\\.([A-Za-z0-9]+)".r

  private def extensionFromGlob(glob: String): Option[String] =
    globExtensionPattern.findFirstMatchIn(glob.replace('\\', '/')).map(_.group(1))

  private def matchesExtensionHint(path: os.Path, hints: Set[String]): Boolean =
    extensionOf(path) match
      case ""  => false
      case ext => hints.contains(ext)

  private def extensionOf(path: os.Path): String =
    val name     = path.last
    val dotIndex = name.lastIndexOf('.')
    if dotIndex < 0 || dotIndex == name.length - 1 then ""
    else name.substring(dotIndex + 1).toLowerCase
