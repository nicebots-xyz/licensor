// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots
import caseapp._
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import config.LicensingConfig
import handlers.{
  AstroHandler,
  CmdHandler,
  CssHandler,
  DuplicateExtensionException,
  HandlerRegistry,
  HashtagHandler,
  HtmlHandler,
  MdxHandler,
  SlashSlashHandler
}

/** Base options for all commands.
  */
@AppName("licensor")
@AppVersion(xyz.nicebots.BuildInfo.version)
abstract class BaseOptions()

/** Common options for all commands.
  *
  * @param config
  *   configuration file path
  * @param ignore
  *   glob patterns to ignore
  * @param verbose
  *   enable verbose logging
  */
@ArgsName("files")
case class CommonOptions(
    @HelpMessage("Configuration file path")
    @Name("c")
    config: String = "licensor-config.yaml",
    @HelpMessage("Glob to ignore (can be specified multiple times)")
    ignore: Vector[String] = Vector.empty,
    @HelpMessage("Enable verbose logging")
    verbose: Option[Boolean] = Some(false)
) extends BaseOptions

/** Empty options.
  */
case class NoOptions() extends BaseOptions

/** Adjust the root logger level.
  *
  * @param level
  *   target log level
  */
def setLogLevel(level: Level): Unit =
  val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
  rootLogger.asInstanceOf[Logger].setLevel(level)

/** Results from common setup.
  *
  * Contains all the data needed for both check and add commands after performing initial setup.
  * This includes the working directory, collected files, loaded license configuration, and the
  * handler registry.
  *
  * @param baseDir
  *   base directory for relative path calculations and file operations
  * @param inputFiles
  *   collected input files matching the glob patterns and not excluded by ignore patterns
  * @param licenseText
  *   license text loaded and formatted from the configuration file
  * @param registry
  *   handler registry mapping file extensions to appropriate handlers
  */
case class SetupContext(
    baseDir: os.Path,
    inputFiles: Vector[os.Path],
    licenseText: String,
    registry: HandlerRegistry
)

/** Perform common setup for commands.
  *
  * Handles verbose logging configuration, file collection from glob patterns, config loading, and
  * handler initialization. This function exits the program with status 1 if no input files are
  * found, if the config file cannot be loaded, or if duplicate extensions are detected in handlers.
  *
  * @param opts
  *   common options including config path, ignore patterns, and verbose flag
  * @param args
  *   remaining arguments containing glob patterns for input files
  * @param logger
  *   logger instance for error reporting
  * @return
  *   setup context containing base directory, input files, license text, and handler registry
  */
def setupCommand(
    opts: CommonOptions,
    args: RemainingArgs,
    logger: org.slf4j.Logger
): SetupContext =
  if opts.verbose.getOrElse(false) then setLogLevel(Level.DEBUG)

  val baseDir    = os.pwd
  val inputGlobs = args.all.toVector
  val inputFiles = CliUtils.collectFiles(inputGlobs, opts.ignore, baseDir)
  if inputFiles.isEmpty then
    logger.error("No input files matched.")
    sys.exit(1)

  val config      = CliUtils.loadConfigOrExit(opts.config, baseDir, logger)
  val licenseText = LicensingConfig.toLicenseText(config)

  val handlerList = Seq(
    new HashtagHandler(),
    new SlashSlashHandler(),
    new AstroHandler(),
    new MdxHandler(),
    new HtmlHandler(),
    new CssHandler(),
    new CmdHandler()
  )

  val registry =
    try {
      new HandlerRegistry(handlerList)
    } catch {
      case e: DuplicateExtensionException =>
        logger.error(s"Handler configuration error: ${e.getMessage}")
        sys.exit(1)
    }

  SetupContext(baseDir, inputFiles, licenseText, registry)

/** Check command entry point.
  *
  * Verifies that all input files contain the expected license header. Exits with a non-zero status
  * if any files are missing the license or have an external license.
  *
  * @example
  *   {{{
  *   licensor check src/**/*.scala
  *   }}}
  */
object CheckCommand extends Command[CommonOptions]:
  override def name  = "check"
  private val logger = LoggerFactory.getLogger(getClass)

  /** Run the check command.
    *
    * Scans all input files and reports license header status. Files without the expected license
    * header or with external licenses cause the command to exit with status 1.
    *
    * @param opts
    *   command options (config path, ignore patterns, verbose flag)
    * @param args
    *   remaining arguments containing glob patterns for files to check
    */
  override def run(opts: CommonOptions, args: RemainingArgs): Unit = {
    val ctx     = setupCommand(opts, args, logger)
    var missing = 0
    var skipped = 0

    ctx.inputFiles.foreach { path =>
      val ext = extensionOf(path)
      ctx.registry.getHandler(ext) match
        case None =>
          skipped += 1
          logger.warn(s"Skipping unsupported file type: ${formatPath(ctx.baseDir, path)}")
        case Some(handler) =>
          val fileDump = Files.readString(path.toNIO, StandardCharsets.UTF_8)
          handler.checkLicense(fileDump, ctx.licenseText) match
            case LicenseState.Present =>
              logger.info(s"Present: ${formatPath(ctx.baseDir, path)}")
            case LicenseState.External =>
              logger.info(s"External: ${formatPath(ctx.baseDir, path)}")
            case LicenseState.Missing =>
              missing += 1
              logger.warn(s"Missing: ${formatPath(ctx.baseDir, path)}")
    }

    logger.info(s"Skipped: $skipped")
    if missing > 0 then sys.exit(1)
  }

/** Add command entry point.
  *
  * Adds the configured license header to all input files that are missing it. Files that already
  * have the correct license header are left unchanged. Exits with a non-zero status if no files
  * were modified.
  *
  * @example
  *   {{{
  *   licensor add src/**/*.scala
  *   }}}
  */
object AddCommand extends Command[CommonOptions]:
  override def name  = "add"
  private val logger = LoggerFactory.getLogger(getClass)

  /** Run the add command.
    *
    * Adds license headers to all input files that don't have one. Files that already have the
    * correct license are skipped. The command exits with status 1 if no files were modified.
    *
    * @param opts
    *   command options (config path, ignore patterns, verbose flag)
    * @param args
    *   remaining arguments containing glob patterns for files to process
    */
  override def run(opts: CommonOptions, args: RemainingArgs): Unit = {
    val ctx     = setupCommand(opts, args, logger)
    var added   = 0
    var skipped = 0

    ctx.inputFiles.foreach { path =>
      val ext = extensionOf(path)
      ctx.registry.getHandler(ext) match
        case None =>
          skipped += 1
          logger.warn(s"Skipping unsupported file type: ${formatPath(ctx.baseDir, path)}")
        case Some(handler) =>
          val fileDump          = Files.readString(path.toNIO, StandardCharsets.UTF_8)
          val (updated, didAdd) = handler.addLicense(fileDump, ctx.licenseText)
          if didAdd then
            Files.writeString(path.toNIO, updated, StandardCharsets.UTF_8)
            added += 1
            logger.info(s"Added: ${formatPath(ctx.baseDir, path)}")
          else logger.info(s"Unchanged: ${formatPath(ctx.baseDir, path)}")
    }

    logger.info(s"Skipped: $skipped")
    if added == 0 then sys.exit(0) else sys.exit(1)
  }

/** Version command entry point.
  *
  * @example
  *   {{{
  *   licensor add src/**/*.scala
  *   }}}
  */
object VersionCommand extends Command[NoOptions]:
  override def name = "version"

  /** Run the version command
    *
    * @param opts
    *   null
    * @param args
    *   remaining arguments containing glob patterns for files to process
    */
  override def run(opts: NoOptions, args: RemainingArgs): Unit = {
    val appVersion = xyz.nicebots.BuildInfo.version

    println(s"licensor $appVersion")

    println("Copyright: NiceBots.xyz")
    println("Released under the MIT license")
    println("Use licensor --help for help")
    println("https://github.com/NiceBots/licensor")

  }

/** CLI entry point.
  *
  * Main application entry point for the licensor tool. Provides commands for checking and adding
  * license headers to source files.
  *
  * Available commands:
  *   - `check`: Verify that files contain the expected license header
  *   - `add`: Add license headers to files that don't have them
  *
  * @example
  *   {{{
  *   licensor check src/**/*.scala
  *   licensor add --config my-config.yaml src/**/*.scala
  *   }}}
  */
object Main extends CommandsEntryPoint:
  override def progName = "licensor"

  override def commands: Seq[Command[?]] = Seq(
    VersionCommand,
    CheckCommand,
    AddCommand
  )

/** Extract the lowercase extension from a path.
  *
  * @param path
  *   input file path
  * @return
  *   extension without a leading dot, or empty string
  */
private def extensionOf(path: os.Path): String =
  val name     = path.last
  val dotIndex = name.lastIndexOf('.')
  if dotIndex < 0 || dotIndex == name.length - 1 then ""
  else name.substring(dotIndex + 1).toLowerCase

/** Format a path relative to a base directory.
  *
  * @param baseDir
  *   base directory for relative paths
  * @param path
  *   absolute path to format
  * @return
  *   relative path with forward slashes
  */
private def formatPath(baseDir: os.Path, path: os.Path): String =
  path.relativeTo(baseDir).toString
