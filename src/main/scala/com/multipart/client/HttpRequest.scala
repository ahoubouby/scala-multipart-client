package com.multipart.client

import scala.concurrent.duration.Duration

/** Generic HTTP request
  */
case class HttpRequest(
  url:     String,
  method:  HttpMethod,
  headers: Map[String, String] = Map.empty,
  body:    Option[RequestBody] = None,
  timeout: Option[Duration]    = None,
)
