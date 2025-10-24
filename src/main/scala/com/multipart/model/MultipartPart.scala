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
