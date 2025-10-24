package com.multipart.model

import com.multipart.client._

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
  def contentType: Option[String] = None // info.contentType

  /** Check if this part is JSON based on content-type or content
    */
  def isJson: Boolean =
    contentType.exists(
      ct => ct.contains("json") || ct.contains("application/json"),
    ) || (data.length > 0 && {
      val firstChar = data(0).toChar
      firstChar == '{' || firstChar == '['
    })

  /** Check if this part is PDF based on content-type or magic bytes
    */
  def isPdf: Boolean =
    contentType.exists(_.contains("pdf")) ||
      (data.length >= 4 && new String(data.take(4)) == "%PDF")

  /** Check if this part is an image based on content-type
    */
  def isImage: Boolean =
    contentType.exists(_.startsWith("image/"))

  /** Check if this part is XML based on content-type or content
    */
  def isXml: Boolean =
    contentType.exists(
      ct => ct.contains("xml") || ct.contains("application/xml"),
    ) || (data.length > 5 && {
      val start = new String(data.take(5))
      start.startsWith("<?xml") || start.startsWith("<")
    })

  /** Get data as UTF-8 string
    */
  def asString: String = new String(data, "UTF-8")

  /** Size of data in bytes
    */
  def size: Int = data.length

  override def toString: String =
    s"MultipartPart(identifier=$identifier, contentType=$contentType, size=$size bytes)"
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
