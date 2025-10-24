package com.multipart.model

import com.multipart.client._

/** Result of parsing a multipart response
  */
case class MultipartResult(
  parts:    Seq[MultipartPart],
  metadata: MultipartMetadata,
) {

  /** Get part by exact identifier
    */
  def getPart(identifier: String): Option[MultipartPart] =
    parts.find(_.identifier == identifier)

  /** Get all parts matching a predicate
    */
  def getPartsByType(predicate: PartInfo => Boolean): Seq[MultipartPart] =
    parts.filter(
      p => predicate(p.info),
    )

  /** Get all parts matching a regex pattern
    */
  def getPartsMatching(pattern: String): Seq[MultipartPart] = {
    val regex = pattern.r
    parts.filter(
      p => regex.matches(p.identifier),
    )
  }

  /** Get all JSON parts
    */
  def jsonParts: Seq[MultipartPart] = parts.filter(_.isJson)

  /** Get all PDF parts
    */
  def pdfParts: Seq[MultipartPart] = parts.filter(_.isPdf)

  /** Get all image parts
    */
  def imageParts: Seq[MultipartPart] = parts.filter(_.isImage)

  /** Get all XML parts
    */
  def xmlParts: Seq[MultipartPart] = parts.filter(_.isXml)

  /** Get parts by content type pattern
    */
  def getPartsByContentType(contentTypePattern: String): Seq[MultipartPart] =
    parts.filter(_.contentType.exists(_.contains(contentTypePattern)))

  /** Convert to a map of identifier -> data
    */
  def toByteMap: Map[String, Array[Byte]] =
    parts
      .map(
        p => p.identifier -> p.data,
      )
      .toMap

  /** Convert to a map of identifier -> part
    */
  def toPartMap: Map[String, MultipartPart] =
    parts
      .map(
        p => p.identifier -> p,
      )
      .toMap

  override def toString: String =
    s"MultipartResult(parts=${parts.size}, format=${metadata.format})"
}

/** Metadata about the multipart response
  */
case class MultipartMetadata(
  format:      MultipartFormat,
  boundary:    String,
  rootPart:    Option[String] = None,
  contentType: String         = "",
)

/** Multipart format types
  */
sealed trait MultipartFormat {
  def name: String
}

case object FormDataFormat extends MultipartFormat {
  override def name: String = "multipart/form-data"
}

case object RelatedFormat extends MultipartFormat {
  override def name: String = "multipart/related"
}

case object MixedFormat extends MultipartFormat {
  override def name: String = "multipart/mixed"
}

case object UnknownFormat extends MultipartFormat {
  override def name: String = "multipart/unknown"
}

object MultipartFormat {
  def fromContentType(contentType: String): MultipartFormat =
    contentType.toLowerCase match {
      case ct if ct.contains("multipart/form-data") => FormDataFormat
      case ct if ct.contains("multipart/related")   => RelatedFormat
      case ct if ct.contains("multipart/mixed")     => MixedFormat
      case _                                        => UnknownFormat
    }
}
