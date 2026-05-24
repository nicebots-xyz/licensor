// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/** Safe, performance-oriented directory traversal for CLI file collection.
  *
  * Skips common VCS and dependency cache directories, does not follow symbolic links, and
  * optionally filters by file extension before glob matching.
  */
object FileWalker:

  /** Directory names skipped during traversal (exact match on the final path segment). */
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

  /** Collect all regular files under `root`, applying optional extension prefiltering.
    *
    * @param root
    *   directory to walk
    * @param extensionHints
    *   when non-empty, only files whose extension (lowercase, no dot) is in this set are returned
    * @param skipDirNames
    *   directory names to prune from the walk
    * @return
    *   files discovered under `root` (not including `root` itself)
    */
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
      result.result()

  /** Infer lowercase extensions from recursive glob patterns ending in a dotted extension. */
  def extensionHintsFromGlobs(globs: Vector[String]): Set[String] =
    globs.flatMap(extensionFromGlob).map(_.toLowerCase).toSet

  private val globExtensionPattern = ".*\\*\\.([A-Za-z0-9]+)".r

  private def extensionFromGlob(glob: String): Option[String] =
    val normalized = glob.replace('\\', '/')
    globExtensionPattern.findFirstMatchIn(normalized).map(_.group(1))

  private def matchesExtensionHint(path: os.Path, hints: Set[String]): Boolean =
    extensionOf(path) match
      case ""  => false
      case ext => hints.contains(ext)

  private def extensionOf(path: os.Path): String =
    val name     = path.last
    val dotIndex = name.lastIndexOf('.')
    if dotIndex < 0 || dotIndex == name.length - 1 then ""
    else name.substring(dotIndex + 1).toLowerCase
