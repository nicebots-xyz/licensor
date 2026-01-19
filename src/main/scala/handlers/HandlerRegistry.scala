// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz
package xyz.nicebots
package handlers

import xyz.nicebots.FileHandler

/** Exception thrown when multiple handlers claim the same extension. */
class DuplicateExtensionException(message: String) extends Exception(message)

/** Registry for mapping file extensions to handlers.
  *
  * @param handlers
  *   sequence of file handlers to register
  */
class HandlerRegistry(handlers: Seq[FileHandler]):
  private val extensionMap: Map[String, FileHandler] = buildExtensionMap(handlers)

  /** Build the extension-to-handler mapping, checking for duplicates.
    *
    * @param handlers
    *   handlers to register
    * @return
    *   map from extension to handler
    * @throws DuplicateExtensionException
    *   if multiple handlers claim the same extension
    */
  private def buildExtensionMap(handlers: Seq[FileHandler]): Map[String, FileHandler] =
    val builder    = scala.collection.mutable.Map[String, FileHandler]()
    val duplicates = scala.collection.mutable.Map[String, Set[String]]()

    handlers.foreach { handler =>
      val handlerName = handler.getClass.getSimpleName
      handler.extensions.foreach { ext =>
        builder.get(ext) match
          case Some(existing) =>
            val existingName = existing.getClass.getSimpleName
            val existingSet  = duplicates.getOrElse(ext, Set(existingName))
            duplicates(ext) = existingSet + handlerName
          case None =>
            builder(ext) = handler
      }
    }

    if duplicates.nonEmpty then
      val messages = duplicates
        .map { case (ext, handlers) =>
          s"  .$ext: ${handlers.mkString(", ")}"
        }
        .mkString("\n")
      throw new DuplicateExtensionException(
        s"Multiple handlers registered for the same extensions:\n$messages"
      )

    builder.toMap

  /** Get the handler for a given extension.
    *
    * @param extension
    *   file extension without dot (e.g., "js", "py")
    * @return
    *   Some(handler) if found, None otherwise
    */
  def getHandler(extension: String): Option[FileHandler] =
    extensionMap.get(extension)

  /** Get all supported extensions.
    *
    * @return
    *   set of all extensions across all handlers
    */
  def supportedExtensions: Set[String] =
    extensionMap.keySet
