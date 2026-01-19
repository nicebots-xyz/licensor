// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

import org.scalatest.funsuite.AnyFunSuite
import org.yaml.snakeyaml.Yaml

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.{List => JList}
import java.util.{Map => JMap}

case class CaseSpec(
    name: String,
    command: String,
    args: Vector[String],
    ignore: Vector[String],
    expectExit: Int,
    expectedOutput: Vector[String]
)

class CliSpec extends AnyFunSuite:
  // Load all test cases
  private val casesRoot = Paths.get("src/test/resources/cases")
  private val indexPath = casesRoot.resolve("index.txt")
  private val caseNames = Files
    .readAllLines(indexPath, StandardCharsets.UTF_8)
    .toArray
    .toVector
    .map(_.toString.trim)
    .filter(_.nonEmpty)

  // Create a separate test for each case
  caseNames.foreach { caseName =>
    test(s"case: $caseName") {
      val caseDir = casesRoot.resolve(caseName)
      val spec    = loadCaseSpec(caseDir.resolve("case.yaml"))
      runCase(caseDir, spec)
    }
  }

  private def runCase(caseDir: Path, spec: CaseSpec): Unit =
    val tempRoot    = Files.createTempDirectory("licensor-case")
    val sourceDir   = tempRoot.resolve("source")
    val expectedDir = tempRoot.resolve("expected")
    val configPath  = tempRoot.resolve("config.yaml")

    copyTree(caseDir.resolve("source"), sourceDir)
    val expectedSource = caseDir.resolve("expected")
    if Files.isDirectory(expectedSource) then copyTree(expectedSource, expectedDir)
    Files.copy(caseDir.resolve("config.yaml"), configPath)
    Files.copy(
      Paths.get("src/test/resources/logback-test.xml"),
      tempRoot.resolve("logback-test.xml")
    )

    val before = snapshot(sourceDir)
    val args   = Vector(spec.command, "-c", "config.yaml") ++
      spec.ignore.flatMap(ig => Vector("--ignore", ig)) ++
      spec.args

    val (exit, output) = runCli(args, tempRoot)
    val normalizedOut  = normalizeOutput(output)

    assert(exit == spec.expectExit, normalizedOut)
    spec.expectedOutput.foreach { line =>
      assert(normalizedOut.contains(line), s"Missing output line: $line")
    }
    val expectedSnapshot =
      if hasAnyFiles(expectedDir) then snapshot(expectedDir) else before
    assert(snapshot(sourceDir) == expectedSnapshot)

  /** Run the CLI in a separate process.
    *
    * @param args
    *   CLI arguments
    * @param workDir
    *   working directory for the process
    * @return
    *   exit code and combined stdout/stderr output
    */
  private def runCli(args: Vector[String], workDir: Path): (Int, String) =
    val javaHome  = System.getProperty("java.home")
    val javaExe   = Path.of(javaHome, "bin", "java").toString
    val classpath = buildClasspath()
    val cmd       = Vector(
      javaExe,
      "-Dlogback.configurationFile=logback-test.xml",
      "-cp",
      classpath,
      "xyz.nicebots.Main"
    ) ++ args
    val pb = new ProcessBuilder(cmd*)
    pb.directory(workDir.toFile)
    pb.redirectErrorStream(true)
    val proc   = pb.start()
    val output = new String(proc.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
    val exit   = proc.waitFor()
    (exit, output)

  /** Build a classpath that includes test and dependency jars.
    *
    * @return
    *   classpath string for a child JVM
    */
  private def buildClasspath(): String =
    val loader = Thread.currentThread().getContextClassLoader
    val urls   = Iterator
      .iterate(loader)(_.getParent)
      .takeWhile(_ != null)
      .flatMap {
        case urlLoader: java.net.URLClassLoader => urlLoader.getURLs.toVector
        case _                                  => Vector.empty
      }
      .toVector
    val entries = urls
      .filter(url => url.getProtocol == "file")
      .map(url => Paths.get(url.toURI).toString)
    val existing = System.getProperty("java.class.path")
    (entries :+ existing)
      .filter(_.nonEmpty)
      .distinct
      .mkString(File.pathSeparator)

  /** Load a case.yaml into a CaseSpec.
    *
    * @param path
    *   path to the case.yaml file
    * @return
    *   parsed case specification
    */
  private def loadCaseSpec(path: Path): CaseSpec =
    val yaml                = Yaml()
    val data: Object | Null = yaml.load(Files.readString(path, StandardCharsets.UTF_8))
    val map                 = data.asInstanceOf[JMap[String, Object]]
    CaseSpec(
      name = stringField(map, "name"),
      command = stringField(map, "command"),
      args = listField(map, "args"),
      ignore = listField(map, "ignore"),
      expectExit = intField(map, "expectExit"),
      expectedOutput = listField(map, "expectedOutput")
    )

  /** Read a required string field.
    *
    * @param map
    *   YAML map
    * @param key
    *   field name
    * @return
    *   string value
    */
  private def stringField(map: JMap[String, Object], key: String): String =
    map.get(key) match
      case s: String => s
      case _         => throw new IllegalArgumentException(s"Missing string field: $key")

  /** Read a required int field.
    *
    * @param map
    *   YAML map
    * @param key
    *   field name
    * @return
    *   int value
    */
  private def intField(map: JMap[String, Object], key: String): Int =
    map.get(key) match
      case n: Number => n.intValue
      case _         => throw new IllegalArgumentException(s"Missing int field: $key")

  /** Read a list of strings field.
    *
    * @param map
    *   YAML map
    * @param key
    *   field name
    * @return
    *   string list
    */
  private def listField(map: JMap[String, Object], key: String): Vector[String] =
    map.get(key) match
      case list: JList[?] => list.toArray.toVector.map(_.toString)
      case null           => Vector.empty
      case _              => throw new IllegalArgumentException(s"Missing list field: $key")

  /** Copy a directory tree.
    *
    * @param source
    *   source directory
    * @param target
    *   target directory
    */
  private def copyTree(source: Path, target: Path): Unit =
    if !Files.isDirectory(source) then return
    val stream = Files.walk(source)
    try
      stream.forEach { path =>
        val rel = source.relativize(path)
        val dst = target.resolve(rel)
        if Files.isDirectory(path) then Files.createDirectories(dst)
        else
          Files.createDirectories(dst.getParent)
          Files.copy(path, dst)
      }
    finally stream.close()

  /** Recursively collect relative file paths and contents.
    *
    * @param root
    *   root directory to scan
    * @return
    *   map of relative paths to file contents
    */
  private def snapshot(root: Path): Map[String, String] =
    if !Files.isDirectory(root) then Map.empty
    else
      val stream = Files.walk(root)
      try
        stream
          .filter(Files.isRegularFile(_))
          .toArray
          .toVector
          .map(_.asInstanceOf[Path])
          .map { path =>
            val rel = root.relativize(path).toString.replace(File.separatorChar, '/')
            rel -> Files.readString(path, StandardCharsets.UTF_8)
          }
          .toMap
      finally stream.close()

  /** Check whether a directory contains any files.
    *
    * @param root
    *   directory to scan
    * @return
    *   true if any regular files are present
    */
  private def hasAnyFiles(root: Path): Boolean =
    if !Files.isDirectory(root) then false
    else
      val stream = Files.walk(root)
      try stream.anyMatch(Files.isRegularFile(_))
      finally stream.close()

  /** Normalize output to make comparisons stable.
    *
    * @param output
    *   raw CLI output
    * @return
    *   trimmed output with normalized line endings
    */
  private def normalizeOutput(output: String): String =
    output.replace("\r\n", "\n").trim
