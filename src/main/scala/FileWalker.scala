// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

/** Safe directory traversal for CLI file collection. */
object FileWalker:

  val DefaultSkipDirNames: Set[String] = Set(
    ".git",
    ".svn",
    ".hg",
    ".bzr",
    "node_modules",
    "target",
    "dist",
    "build",
    "out",
    ".gradle",
    ".m2",
    ".ivy2",
    ".coursier",
    "__pycache__",
    ".pytest_cache",
    ".tox",
    ".venv",
    "venv",
    ".idea",
    ".cursor",
    ".next",
    ".nuxt",
    ".svelte-kit",
    ".turbo",
    "coverage"
  )

  def listFiles(
      root: os.Path,
      extensionHints: Set[String] = Set.empty,
      skipDirNames: Set[String] = DefaultSkipDirNames
  ): Vector[os.Path] =
    if !os.exists(root) then Vector.empty
    else if os.isFile(root) then Vector(root)
    else
      val result  = Vector.newBuilder[os.Path]
      val visitor = new SimpleFileVisitor[Path]:
        override def preVisitDirectory(
            dir: Path,
            attrs: BasicFileAttributes
        ): FileVisitResult =
          if attrs.isSymbolicLink then FileVisitResult.SKIP_SUBTREE
          else if dir == root.toNIO then super.preVisitDirectory(dir, attrs)
          else if skipDirNames.contains(dir.getFileName.toString) then FileVisitResult.SKIP_SUBTREE
          else super.preVisitDirectory(dir, attrs)

        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult =
          if attrs.isRegularFile && !attrs.isSymbolicLink then
            val path = os.Path(file)
            if extensionHints.isEmpty || matchesExtensionHint(path, extensionHints) then
              result += path
          FileVisitResult.CONTINUE

        override def visitFileFailed(file: Path, exc: IOException): FileVisitResult =
          FileVisitResult.CONTINUE

      try Files.walkFileTree(root.toNIO, visitor)
      catch case _: IOException => ()
      result
        .result()
        .filterNot(path => hasSkippedSegment(root, path, skipDirNames))

  def extensionHintsFromGlobs(globs: Vector[String]): Set[String] =
    globs.flatMap(extensionFromGlob).map(_.toLowerCase).toSet

  private def hasSkippedSegment(
      root: os.Path,
      path: os.Path,
      skipDirNames: Set[String]
  ): Boolean =
    val segments =
      try path.relativeTo(root).segments
      catch case _: Throwable => Vector.empty[String]
    segments.exists(skipDirNames.contains)

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
