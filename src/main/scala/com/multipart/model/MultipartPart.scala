package com.multipart.model

import com.multipart.client._
import com.multipart.utils.ContentTypeDetector

/** A single part from a multipart response
  */
case class MultipartPart(
  info: PartInfo,
  data: Array[Byte],
) {

  /** Identifier for this part (content-id, name, etc.)
    */
  def identifier: String = info.identifier

  /** Content-Type header if present
    */
  def contentType: Option[String] = info match {
    case f: FormDataPartInfo => f.contentType
    case r: RelatedPartInfo  => r.contentType
    case u: UnknownPartInfo  => u.headers.get("content-type")
  }

  /** Check if this part is JSON based on content-type or content
    */
  def isJson: Boolean = ContentTypeDetector.isJson(contentType, data)

  /** Check if this part is PDF based on content-type or magic bytes
    */
  def isPdf: Boolean = ContentTypeDetector.isPdf(contentType, data)

  /** Check if this part is an image based on content-type
    */
  def isImage: Boolean = ContentTypeDetector.isImage(contentType)

  /** Check if this part is XML based on content-type or content
    */
  def isXml: Boolean = ContentTypeDetector.isXml(contentType, data)

  /** Check if this part is Excel/spreadsheet based on content-type
    */
  def isExcel: Boolean = contentType.exists { ct =>
    ct.contains("spreadsheet") ||
      ct.contains("excel") ||
      ct.contains("application/vnd.ms-excel") ||
      ct.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  }

  /** Check if this part is CSV based on content-type
    */
  def isCsv: Boolean = contentType.exists(_.contains("text/csv"))

  /** Check if this part is a text file based on content-type
    */
  def isText: Boolean = contentType.exists(_.startsWith("text/"))

  /** Check if this part is binary data
    */
  def isBinary: Boolean = !isText

  /** Get data as UTF-8 string
    */
  def asString: String = new String(data, "UTF-8")

  /** Get data as string with specific encoding
    */
  def asString(encoding: String): String = new String(data, encoding)

  /** Get filename if available (for form-data parts)
    */
  def filename: Option[String] = info match {
    case f: FormDataPartInfo => f.filename
    case _                   => None
  }

  /** Get content ID if available (for related/mixed parts)
    */
  def contentId: Option[String] = info match {
    case r: RelatedPartInfo => Some(r.contentId)
    case _                  => None
  }

  /** Get content location if available (for related parts)
    */
  def contentLocation: Option[String] = info match {
    case r: RelatedPartInfo => r.contentLocation
    case _                  => None
  }

  /** Get all headers as a map
    */
  def headers: Map[String, String] = info.metadata

  /** Size of data in bytes
    */
  def size: Int = data.length

  /** Size in kilobytes (rounded)
    */
  def sizeKB: Double = size / 1024.0

  /** Size in megabytes (rounded)
    */
  def sizeMB: Double = size / (1024.0 * 1024.0)

  /** Human-readable size string
    */
  def sizeFormatted: String = {
    if (size < 1024) s"$size B"
    else if (size < 1024 * 1024) f"$sizeKB%.2f KB"
    else f"$sizeMB%.2f MB"
  }

  /** Check if PDF starts with valid magic bytes
    */
  def isValidPdf: Boolean =
    isPdf && data.length >= 4 && data.take(4).mkString == "%PDF"

  /** Detect image format from magic bytes
    */
  def imageFormat: Option[String] = {
    if (!isImage) None
    else if (data.length >= 2 && data(0) == 0xFF.toByte && data(1) == 0xD8.toByte) Some("JPEG")
    else if (data.length >= 8 && data.take(8).mkString == "‰PNG\r\n\u001A\n") Some("PNG")
    else if (data.length >= 6 && new String(data.take(6)) == "GIF89a") Some("GIF")
    else if (data.length >= 6 && new String(data.take(6)) == "GIF87a") Some("GIF")
    else contentType.map(_.split("/").lastOption.getOrElse("unknown").toUpperCase)
  }

  override def toString: String =
    s"MultipartPart(identifier=$identifier, contentType=$contentType, size=$sizeFormatted)"
}

object MultipartPart {

  /** Create a MultipartPart with FormDataPartInfo
    */
  def formData(
    name:        String,
    data:        Array[Byte],
    filename:    Option[String] = None,
    contentType: Option[String] = None,
  ): MultipartPart =
    MultipartPart(
      FormDataPartInfo(name, filename, contentType),
      data,
    )

  /** Create a MultipartPart with RelatedPartInfo
    */
  def related(
    contentId:   String,
    data:        Array[Byte],
    contentType: Option[String] = None,
  ): MultipartPart =
    MultipartPart(
      RelatedPartInfo(contentId, contentType, None),
      data,
    )
}
