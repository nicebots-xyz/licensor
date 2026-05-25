// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

/** Terminal UX: semantic color, symbols, and layout for user-facing CLI output.
  *
  * Philosophy (aligned with clig.dev / modern CLI practice):
  *   - Paths and bulk text stay unstyled for readability and scriptability.
  *   - Color marks meaning (failure, success, muted structure) — not decoration.
  *   - Symbols reinforce status; ASCII fallbacks when plain or NO_COLOR.
  *   - Progress is transient and understated; results are the focus.
  */
object CliUx:

  private var forcePlain: Boolean = false

  def configure(noColor: Boolean): Unit =
    forcePlain = noColor

  private def plainOutput: Boolean =
    forcePlain ||
      sys.env.contains("NO_COLOR") ||
      sys.env.contains("LICENSOR_PLAIN_OUTPUT") ||
      sys.env.contains("CI")

  def colorEnabled: Boolean =
    !plainOutput && (sys.env.contains("FORCE_COLOR") || System.console() != null)

  def unicodeSymbols: Boolean = colorEnabled && !sys.env.contains("LICENSOR_ASCII")

  object Symbols:
    def ok: String    = if unicodeSymbols then "✓" else "OK"
    def fail: String  = if unicodeSymbols then "✗" else "x"
    def added: String = if unicodeSymbols then "+" else "+"
    def sep: String   = "-"
    def rule: String  = "-"

  def fatal(message: String): Nothing =
    System.err.println(renderError(message))
    sys.exit(1)

  def renderError(message: String): String =
    if colorEnabled then fansi.Color.Red(s"${Symbols.fail} $message").render
    else s"${Symbols.fail} $message"

  def dim(text: String): String =
    if colorEnabled then fansi.Color.DarkGray(text).render else text

  def emphasis(text: String): String =
    if colorEnabled then fansi.Bold.On(text).render else text

  def label(text: String): String = dim(text)

  def pathText(path: String): String = path

  def statusWord(kind: StatusKind, word: String): String =
    if !colorEnabled then word
    else
      kind match
        case StatusKind.Missing  => fansi.Color.Red(word).render
        case StatusKind.Added    => fansi.Color.Green(word).render
        case StatusKind.External => fansi.Color.Cyan(word).render
        case StatusKind.Ok       => fansi.Color.Green(word).render
        case StatusKind.Muted    => fansi.Color.DarkGray(word).render

  enum StatusKind:
    case Missing, Added, External, Ok, Muted

  def line(text: String): Unit = println(text)

  def blank(): Unit = println()

  def rule(width: Int): Unit = line(dim(Symbols.rule * width))

  /** Visible character length after stripping ANSI escape sequences. */
  def visibleLength(text: String): Int =
    text.replaceAll("\u001b\\[[0-9;]*[A-Za-z]", "").length

  def commandHeader(command: String, fileCount: Int): Unit =
    val noun = if fileCount == 1 then "file" else "files"
    line(s"${emphasis(command)} ${Symbols.sep} $fileCount $noun")

  final class Progress(total: Int, label: String = "Scanning"):
    private var lastLen = 0

    def update(current: Int): Unit =
      if !shouldShow then return
      val text = dim(s"  $label ($current/$total)")
      val pad  = " " * math.max(0, lastLen - text.length)
      print(s"\r$text$pad")
      lastLen = text.length

    /** Clear the transient progress line before printing a permanent result line. */
    def clearForOutput(): Unit =
      if shouldShow && lastLen > 0 then
        print(s"\r${" " * lastLen}\r")
        lastLen = 0

    def finish(): Unit = clearForOutput()

    private def shouldShow: Boolean =
      colorEnabled && System.console() != null && total > 1

  def missingLine(path: String): Unit =
    line(s"${Symbols.fail} ${statusWord(StatusKind.Missing, "missing")}  ${pathText(path)}")

  def addedLine(path: String): Unit =
    line(s"${Symbols.added} ${statusWord(StatusKind.Added, "added")}  ${pathText(path)}")

  def presentLine(path: String): Unit =
    line(s"${Symbols.ok} ${statusWord(StatusKind.Ok, "present")}  ${pathText(path)}")

  def unchangedLine(path: String): Unit =
    line(s"${dim(Symbols.sep)} ${statusWord(StatusKind.Muted, "unchanged")}  ${pathText(path)}")

  def externalSection(paths: Seq[String]): Unit =
    if paths.isEmpty then return
    blank()
    line(label("External"))
    paths.sorted.distinct.foreach(p => line(s"  ${pathText(p)}"))

  def summaryCheck(checked: Int, missing: Int, external: Int): Unit =
    blank()
    val parts = Vector.newBuilder[String]
    parts += dim(s"$checked checked")
    if missing > 0 then parts += statusWord(StatusKind.Missing, s"$missing missing")
    else if external == 0 then parts += statusWord(StatusKind.Ok, "all clear")
    if external > 0 then parts += statusWord(StatusKind.External, s"$external external")
    val summary = parts.result().mkString(s" ${Symbols.sep} ")
    rule(visibleLength(summary))
    line(summary)

  def summaryAdd(processed: Int, added: Int): Unit =
    blank()
    val summary =
      if added > 0 then
        statusWord(StatusKind.Added, s"$added ${plural(added, "file")} updated") +
          dim(s" ${Symbols.sep} $processed scanned")
      else dim(s"No changes across $processed ${plural(processed, "file")}")
    rule(visibleLength(summary))
    line(summary)

  private def plural(count: Int, word: String): String =
    if count == 1 then word else s"${word}s"
