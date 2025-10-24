package com.multipart.client

import scala.concurrent.Future

/** Abstract HTTP client interface
  */
trait HttpClient {

  /** Execute an HTTP request and return a streaming response
    */
  def execute(request: HttpRequest): Future[HttpResponse]
}

/** Request body types
  */
sealed trait RequestBody

case class JsonBody(value: play.api.libs.json.JsValue) extends RequestBody

case class StringBody(value: String, contentType: String = "text/plain") extends RequestBody

case class BytesBody(value: Array[Byte], contentType: String = "application/octet-stream") extends RequestBody
