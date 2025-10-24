package com.multipart.parser

import org.apache.pekko.util.ByteString

object Part {

  /** Raw part type from parser:
    *   - Left(Part) = Part metadata
    *   - Right(ByteString) = Part body chunk
    */
  type RawPart = Either[Part[Unit], ByteString]
}

/** Multipart part types used during parsing
  */
sealed trait Part[+A]

/** A data part (simple form field)
  */
case class DataPart(key: String, value: ByteString) extends Part[Nothing]

/** A file part (file upload)
  * @param ref
  *   The reference to the file body (streaming source or unit during parsing)
  */
case class FilePart[A](
  key:         String,
  filename:    String,
  contentType: Option[String],
  ref:         A,
) extends Part[A]

/** A part that couldn't be classified (may be multipart/related or malformed)
  */
case class BadPart(
  headers: Map[String, String],
  value:   ByteString,
) extends Part[Nothing]

/** Parse error occurred
  */
case class ParseError(message: String) extends Part[Nothing]

/** Memory buffer was exceeded
  */
case class MaxMemoryBufferExceeded(message: String) extends Part[Nothing]
