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

@AppName("licensor")
@AppVersion(xyz.nicebots.BuildInfo.version)
abstract class BaseOptions()

@ArgsName("files")
case class CommonOptions(
    @HelpMessage("Configuration file path")
    @Name("c")
    config: String = "licensor-config.yaml",
    @HelpMessage("Glob to ignore (can be specified multiple times)")
    ignore: Vector[String] = Vector.empty,
    @HelpMessage("Respect .gitignore patterns when collecting files (default: enabled)")
    @Name("respect-gitignore")
    respectGitignore: Option[Boolean] = Some(true),
    @HelpMessage("Enable verbose logging and per-file details")
    verbose: Boolean = false,
    @HelpMessage("Disable color and Unicode symbols")
    @Name("no-color")
    noColor: Boolean = false
) extends BaseOptions

case class NoOptions() extends BaseOptions

def setLogLevel(level: Level): Unit =
  val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
  rootLogger.asInstanceOf[Logger].setLevel(level)

def applyColorOptions(opts: CommonOptions): Unit =
  CliUx.configure(opts.noColor)

case class SetupContext(
    baseDir: os.Path,
    inputFiles: Vector[os.Path],
    licenseText: String,
    registry: HandlerRegistry
)

def setupCommand(
    opts: CommonOptions,
    args: RemainingArgs,
    logger: org.slf4j.Logger
): SetupContext =
  applyColorOptions(opts)
  if opts.verbose then setLogLevel(Level.DEBUG)

  val baseDir          = os.pwd
  val inputGlobs       = args.all.toVector
  val config           = CliUtils.loadConfigOrExit(opts.config, baseDir, logger)
  val mergedIgnores    = config.ignores ++ opts.ignore
  val respectGitignore = opts.respectGitignore.getOrElse(true)
  val inputFiles       =
    CliUtils.collectFiles(inputGlobs, mergedIgnores, baseDir, respectGitignore)
  if inputFiles.isEmpty then CliUx.fatal("No input files matched.")

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
    try new HandlerRegistry(handlerList)
    catch
      case e: DuplicateExtensionException =>
        CliUx.fatal(s"Handler configuration error: ${e.getMessage}")

  SetupContext(baseDir, inputFiles, licenseText, registry)

object CheckCommand extends Command[CommonOptions]:
  override def name  = "check"
  private val logger = LoggerFactory.getLogger(getClass)

  override def run(opts: CommonOptions, args: RemainingArgs): Unit =
    val ctx     = setupCommand(opts, args, logger)
    val targets = loadProcessable(ctx)
    if targets.isEmpty then CliUx.fatal("No processable files matched.")

    val externalPaths = Vector.newBuilder[String]
    var missing       = 0
    val progress      = CliUx.Progress(targets.length, "Checking")

    CliUx.commandHeader("check", targets.length)

    targets.zipWithIndex.foreach { case (file, idx) =>
      progress.update(idx + 1, file.relPath)
      file.handler.checkLicense(file.content, ctx.licenseText) match
        case LicenseState.Present =>
          if opts.verbose then
            logger.debug(s"Present: ${file.relPath}")
            CliUx.presentLine(file.relPath)
        case LicenseState.External =>
          externalPaths += file.relPath
          if opts.verbose then logger.debug(s"External: ${file.relPath}")
        case LicenseState.Missing =>
          missing += 1
          CliUx.missingLine(file.relPath)
          if opts.verbose then logger.debug(s"Missing: ${file.relPath}")
    }

    progress.finish()
    val externals = externalPaths.result().distinct
    CliUx.externalSection(externals)
    CliUx.summaryCheck(targets.length, missing, externals.size)
    if missing > 0 then sys.exit(1)

object AddCommand extends Command[CommonOptions]:
  override def name  = "add"
  private val logger = LoggerFactory.getLogger(getClass)

  override def run(opts: CommonOptions, args: RemainingArgs): Unit =
    val ctx     = setupCommand(opts, args, logger)
    val targets = loadProcessable(ctx)
    if targets.isEmpty then CliUx.fatal("No processable files matched.")

    var added    = 0
    val progress = CliUx.Progress(targets.length, "Updating")

    CliUx.commandHeader("add", targets.length)

    targets.zipWithIndex.foreach { case (file, idx) =>
      progress.update(idx + 1, file.relPath)
      val (updated, didAdd) = file.handler.addLicense(file.content, ctx.licenseText)
      if didAdd then
        Files.writeString(file.path.toNIO, updated, StandardCharsets.UTF_8)
        added += 1
        CliUx.addedLine(file.relPath)
        if opts.verbose then logger.debug(s"Added: ${file.relPath}")
      else if opts.verbose then
        logger.debug(s"Unchanged: ${file.relPath}")
        CliUx.unchangedLine(file.relPath)
    }

    progress.finish()
    CliUx.summaryAdd(targets.length, added)
    if added == 0 then sys.exit(0) else sys.exit(1)

object VersionCommand extends Command[NoOptions]:
  override def name = "version"

  override def run(opts: NoOptions, args: RemainingArgs): Unit =
    CliUx.line(s"licensor ${xyz.nicebots.BuildInfo.version}")
    CliUx.line(CliUx.dim("https://github.com/NiceBots/licensor"))

object Main extends CommandsEntryPoint:
  override def progName = "licensor"

  override def commands: Seq[Command[?]] = Seq(
    VersionCommand,
    CheckCommand,
    AddCommand
  )

private case class LoadedFile(
    path: os.Path,
    relPath: String,
    handler: FileHandler,
    content: String
)

private def loadProcessable(ctx: SetupContext): Vector[LoadedFile] =
  ctx.inputFiles.flatMap { path =>
    ctx.registry.getHandler(extensionOf(path)).flatMap { handler =>
      TextFiles.readUtf8(path).map { content =>
        LoadedFile(path, formatPath(ctx.baseDir, path), handler, content)
      }
    }
  }

private def extensionOf(path: os.Path): String =
  val name     = path.last
  val dotIndex = name.lastIndexOf('.')
  if dotIndex < 0 || dotIndex == name.length - 1 then ""
  else name.substring(dotIndex + 1).toLowerCase

private def formatPath(baseDir: os.Path, path: os.Path): String =
  path.relativeTo(baseDir).toString
