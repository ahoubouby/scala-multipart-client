package com.multipart.utils

/** Utility for detecting content types from headers or magic bytes
  *
  * This object provides methods to identify common file types based on:
  *   - Content-Type header values
  *   - Magic bytes (file signatures) in the content
  */
object ContentTypeDetector {

  /** Check if content is JSON based on content-type or content analysis
    *
    * Detection strategies:
    *   1. Content-Type contains "json" or "application/json" (case-insensitive)
    *   2. First byte is '{' or '[' (JSON objects/arrays)
    *
    * @param contentType Optional Content-Type header value
    * @param data The actual content bytes
    * @return true if content appears to be JSON
    */
  def isJson(contentType: Option[String], data: Array[Byte]): Boolean =
    contentType.exists(ct => {
      val lower = ct.toLowerCase
      lower.contains("json") || lower.contains("application/json")
    }) ||
      (data.length > 0 && {
        val firstChar = data(0).toChar
        firstChar == '{' || firstChar == '['
      })

  /** Check if content is PDF based on content-type or magic bytes
    *
    * Detection strategies:
    *   1. Content-Type contains "pdf" (case-insensitive)
    *   2. First 4 bytes are "%PDF" (PDF magic signature)
    *
    * @param contentType Optional Content-Type header value
    * @param data The actual content bytes
    * @return true if content appears to be PDF
    */
  def isPdf(contentType: Option[String], data: Array[Byte]): Boolean =
    contentType.exists(_.toLowerCase.contains("pdf")) ||
      (data.length >= 4 && new String(data.take(4)) == "%PDF")

  /** Check if content is an image based on content-type
    *
    * Detection strategy:
    *   - Content-Type starts with "image/" (case-insensitive)
    *
    * @param contentType Optional Content-Type header value
    * @return true if content appears to be an image
    */
  def isImage(contentType: Option[String]): Boolean =
    contentType.exists(_.toLowerCase.startsWith("image/"))

  /** Check if content is XML based on content-type or content analysis
    *
    * Detection strategies:
    *   1. Content-Type contains "xml" or "application/xml" (case-insensitive)
    *   2. First 5 bytes start with "<?xml" or "<" (XML declaration or tag)
    *
    * @param contentType Optional Content-Type header value
    * @param data The actual content bytes
    * @return true if content appears to be XML
    */
  def isXml(contentType: Option[String], data: Array[Byte]): Boolean =
    contentType.exists(ct => {
      val lower = ct.toLowerCase
      lower.contains("xml") || lower.contains("application/xml")
    }) ||
      (data.length > 5 && {
        val start = new String(data.take(5))
        start.startsWith("<?xml") || start.startsWith("<")
      })

  /** Detect the likely content type from both header and content analysis
    *
    * This method attempts to identify the content type by examining both
    * the Content-Type header and the actual content bytes.
    *
    * @param contentType Optional Content-Type header value
    * @param data The actual content bytes
    * @return A descriptive string of the detected type, or "unknown"
    */
  def detectType(contentType: Option[String], data: Array[Byte]): String = {
    if (isJson(contentType, data)) "json"
    else if (isPdf(contentType, data)) "pdf"
    else if (isXml(contentType, data)) "xml"
    else if (isImage(contentType)) "image"
    else contentType.getOrElse("unknown")
  }

  /** Extract the MIME type from a Content-Type header
    *
    * Strips parameters like charset, boundary, etc.
    * Example: "text/html; charset=utf-8" -> "text/html"
    *
    * @param contentType The full Content-Type header value
    * @return Just the MIME type portion
    */
  def extractMimeType(contentType: String): String = {
    val semicolonIndex = contentType.indexOf(';')
    if (semicolonIndex > 0) contentType.substring(0, semicolonIndex).trim
    else contentType.trim
  }

  /** Check if a content type matches a pattern (case-insensitive)
    *
    * @param contentType The Content-Type to check
    * @param pattern The pattern to match (e.g., "json", "pdf", "image")
    * @return true if the content type contains the pattern (case-insensitive)
    */
  def matches(contentType: Option[String], pattern: String): Boolean =
    contentType.exists(_.toLowerCase.contains(pattern.toLowerCase))
}
