package com.multipart.client

/** Information extracted from part headers
  */

sealed trait PartInfo {
  def identifier: String // Generic key (name, content-id, etc.)

  def metadata: Map[String, String]
}

case class FormDataPartInfo(
  name:        String,
  filename:    Option[String],
  contentType: Option[String],
) extends PartInfo {
  def identifier: String = name

  def metadata: Map[String, String] = Map(
    "filename"     -> filename,
    "content-type" -> contentType,
  ).collect {
    case (k, Some(v)) => k -> v
  }
}

case class RelatedPartInfo(
  contentId:       String,
  contentType:     Option[String],
  contentLocation: Option[String],
) extends PartInfo {
  def identifier: String = contentId

  def metadata: Map[String, String] = Map(
    "content-type"     -> contentType,
    "content-location" -> contentLocation,
  ).collect {
    case (k, Some(v)) => k -> v
  }
}

case class UnknownPartInfo(
  headers: Map[String, String],
) extends PartInfo {
  def identifier: String = headers.getOrElse("content-id", "<unknown>")

  def metadata: Map[String, String] = headers
}
