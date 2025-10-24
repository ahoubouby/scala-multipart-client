package com.multipart.client

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

/** Generic HTTP response interface
  */
trait HttpResponse {

  /** HTTP status code
    */
  def status: Int

  /** Response headers (lowercase keys)
    */
  def headers: Map[String, Seq[String]]

  /** Body as streaming source
    */
  def bodyAsSource: Source[ByteString, _]

  /** Get single header value
    */
  def header(name: String): Option[String] =
    headers.get(name.toLowerCase).flatMap(_.headOption)

  /** Check if response is successful (2xx)
    */
  def isSuccess: Boolean = status >= 200 && status < 300

  /** Check if response is multipart
    */
  def isMultipart: Boolean =
    header("content-type").exists(_.startsWith("multipart/"))
}
