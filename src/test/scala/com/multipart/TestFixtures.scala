package com.multipart

import org.apache.pekko.util.ByteString

import java.nio.charset.StandardCharsets

/** Common test fixtures and sample data for multipart testing
  */
object TestFixtures {

  // ========================================
  // Sample Multipart Bodies
  // ========================================

  val simpleBoundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"

  /** Simple multipart/form-data with two text fields
    */
  def simpleFormData(boundary: String = simpleBoundary): String =
    s"""------$boundary\r
       |Content-Disposition: form-data; name="field1"\r
       |\r
       |value1\r
       |------$boundary\r
       |Content-Disposition: form-data; name="field2"\r
       |\r
       |value2\r
       |------$boundary--\r
       |""".stripMargin

  /** Multipart/form-data with a file upload
    */
  def formDataWithFile(boundary: String = simpleBoundary): String =
    s"""------$boundary\r
       |Content-Disposition: form-data; name="username"\r
       |\r
       |john_doe\r
       |------$boundary\r
       |Content-Disposition: form-data; name="avatar"; filename="photo.jpg"\r
       |Content-Type: image/jpeg\r
       |\r
       |FAKE_IMAGE_DATA\r
       |------$boundary--\r
       |""".stripMargin

  /** Multipart/related with JSON and PDF
    */
  def relatedMultipart(boundary: String = "boundary123"): String =
    s"""------$boundary\r
       |Content-ID: <metadata>\r
       |Content-Type: application/json\r
       |\r
       |{"id": "123", "type": "label"}\r
       |------$boundary\r
       |Content-ID: <label>\r
       |Content-Type: application/pdf\r
       |\r
       |%PDF-1.4 fake pdf content\r
       |------$boundary--\r
       |""".stripMargin

  /** Multipart/mixed with various content types
    */
  def mixedMultipart(boundary: String = "mixed-boundary"): String =
    s"""------$boundary\r
       |Content-ID: <part1>\r
       |Content-Type: text/plain\r
       |\r
       |Plain text content\r
       |------$boundary\r
       |Content-Location: /path/to/image\r
       |Content-Type: image/png\r
       |\r
       |PNG_DATA_HERE\r
       |------$boundary--\r
       |""".stripMargin

  // ========================================
  // Sample Content for Type Detection
  // ========================================

  val jsonContent: Array[Byte]      = """{"key": "value"}""".getBytes(StandardCharsets.UTF_8)
  val jsonArrayContent: Array[Byte] = """[1, 2, 3]""".getBytes(StandardCharsets.UTF_8)
  val pdfContent: Array[Byte]       = "%PDF-1.4\nfake pdf".getBytes(StandardCharsets.UTF_8)
  val xmlContent: Array[Byte]       = """<?xml version="1.0"?><root></root>""".getBytes(StandardCharsets.UTF_8)
  val plainTextContent: Array[Byte] = "plain text".getBytes(StandardCharsets.UTF_8)

  // ========================================
  // Sample Headers
  // ========================================

  val formDataHeaders: Map[String, String] = Map(
    "content-disposition" -> """form-data; name="field1"""",
    "content-type"        -> "text/plain",
  )

  val formDataFileHeaders: Map[String, String] = Map(
    "content-disposition" -> """form-data; name="file"; filename="document.pdf"""",
    "content-type"        -> "application/pdf",
  )

  val relatedHeaders: Map[String, String] = Map(
    "content-id"   -> "<metadata>",
    "content-type" -> "application/json",
  )

  val mixedHeaders: Map[String, String] = Map(
    "content-id"       -> "<part1>",
    "content-location" -> "/path/to/resource",
    "content-type"     -> "image/png",
  )

  // ========================================
  // Helper Methods
  // ========================================

  /** Convert string to ByteString for stream testing
    */
  def toByteString(content: String): ByteString =
    ByteString(content.getBytes(StandardCharsets.UTF_8))

  /** Create a simple multipart content-type header
    */
  def contentTypeHeader(format: String, boundary: String): String =
    s"$format; boundary=$boundary"

  /** Create a multipart/form-data content-type header
    */
  def formDataContentType(boundary: String = simpleBoundary): String =
    contentTypeHeader("multipart/form-data", boundary)

  /** Create a multipart/related content-type header
    */
  def relatedContentType(boundary: String = "boundary123", start: Option[String] = None): String = {
    val base = contentTypeHeader("multipart/related", boundary)
    start.map(s => s"""$base; start="$s"""").getOrElse(base)
  }

  /** Create a multipart/mixed content-type header
    */
  def mixedContentType(boundary: String = "mixed-boundary"): String =
    contentTypeHeader("multipart/mixed", boundary)
}
