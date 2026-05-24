// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots

import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** Safe UTF-8 text file reads for license processing. */
object TextFiles:

  private val utf8Decoder: CharsetDecoder =
    StandardCharsets.UTF_8
      .newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)

  /** Read a file as UTF-8 text when it decodes cleanly.
    *
    * @param path
    *   file to read
    * @return
    *   decoded contents, or `None` for missing files, directories, or non-text/binary input
    */
  def readUtf8(path: os.Path): Option[String] =
    if !os.isFile(path) then None
    else
      try
        val bytes = Files.readAllBytes(path.toNIO)
        if bytes.isEmpty then Some("")
        else
          val buffer = java.nio.ByteBuffer.wrap(bytes)
          Some(utf8Decoder.decode(buffer).toString)
      catch case _: Exception => None
