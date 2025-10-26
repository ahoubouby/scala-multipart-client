package com.multipart.parser

import com.multipart.client.HttpResponse
import com.multipart.model._
import com.typesafe.scalalogging.LazyLogging

/** Automatic detection of multipart format from HTTP response
  */
object FormatDetector extends LazyLogging {

  /** Detect multipart format from response headers
    */
  def detect(response: HttpResponse): DetectedFormat = {
    val contentType = response
      .header("content-type")
      .getOrElse(throw new Exception("No Content-Type header found"))

    logger.info(s"Detecting format from Content-Type: $contentType")

    val boundary = extractBoundary(contentType)
      .getOrElse(throw new Exception("No boundary parameter found in Content-Type"))

    val format   = detectFormat(contentType)
    val rootPart = extractRootPart(contentType)

    logger.info(s"Format detected: ${format.name}, boundary length: ${boundary.length}")
    rootPart.foreach(
      root => logger.debug(s"Root part: $root"),
    )

    DetectedFormat(
      format      = format,
      boundary    = boundary,
      rootPart    = rootPart,
      contentType = contentType,
    )
  }

  /** Detect multipart format type from Content-Type header
    */
  private def detectFormat(contentType: String): MultipartFormat = {
    val ct = contentType.toLowerCase

    val format = ct match {
      case _ if ct.contains("multipart/form-data") => FormDataFormat
      case _ if ct.contains("multipart/related")   => RelatedFormat
      case _ if ct.contains("multipart/mixed")     => MixedFormat
      case _ if ct.contains("multipart/")          => UnknownFormat
      case _                                       => throw new Exception(s"Not a multipart Content-Type: $contentType")
    }

    logger.debug(s"Format identified as: ${format.name}")
    format
  }

  /** Extract boundary parameter from Content-Type header
    *
    * Examples: multipart/related; boundary="abc123" multipart/form-data; boundary=abc123 multipart/mixed;
    * boundary="----WebKitFormBoundary7MA4YWxkTrZu0gW"
    */
  private def extractBoundary(contentType: String): Option[String] = {
    val BoundaryPattern = """boundary="?([^";]+)"?""".r

    val boundary = BoundaryPattern.findFirstMatchIn(contentType).map(_.group(1))

    boundary match {
      case Some(b) =>
        logger.debug(s"Boundary extracted: '$b' (length: ${b.length})")
      case None =>
        logger.warn(s"No boundary found in Content-Type: $contentType")
    }

    boundary
  }

  /** Extract start parameter from Content-Type header (for multipart/related)
    *
    * Example: multipart/related; boundary="abc"; start="<root>"
    */
  private def extractRootPart(contentType: String): Option[String] = {
    val StartPattern = """start="?([^";]+)"?""".r

    val start = StartPattern.findFirstMatchIn(contentType).map(_.group(1))

    start.foreach(
      s => logger.info(s"Start parameter found: $s"),
    )

    start
  }
}

/** Detected multipart format information
  */
case class DetectedFormat(
  format:      MultipartFormat,
  boundary:    String,
  rootPart:    Option[String],
  contentType: String,
) {

  /** Convert to parser configuration
    */
  def toParserConfig(
    maxMemoryBufferSize: Int =1024 * 1024,
    maxHeaderSize:       Int =4096,
  ): MultipartParserConfig = {

    // Select classifiers based on detected format
    val classifiers = format match {
      case FormDataFormat =>
        Seq(
          com.multipart.classifier.FormDataClassifier,
          com.multipart.classifier.RelatedClassifier,
          com.multipart.classifier.UnknownClassifier,
        )

      case RelatedFormat =>
        Seq(
          com.multipart.classifier.RelatedClassifier,
          com.multipart.classifier.FormDataClassifier,
          com.multipart.classifier.UnknownClassifier,
        )

      case MixedFormat =>
        Seq(
          com.multipart.classifier.MixedClassifier,
          com.multipart.classifier.RelatedClassifier,
          com.multipart.classifier.FormDataClassifier,
          com.multipart.classifier.UnknownClassifier,
        )

      case UnknownFormat =>
        Seq(
          com.multipart.classifier.FormDataClassifier,
          com.multipart.classifier.RelatedClassifier,
          com.multipart.classifier.MixedClassifier,
          com.multipart.classifier.UnknownClassifier,
        )
    }

    MultipartParserConfig(
      boundary            = boundary,
      maxMemoryBufferSize = maxMemoryBufferSize,
      maxHeaderSize       = maxHeaderSize,
      classifiers         = classifiers,
    )
  }
}
