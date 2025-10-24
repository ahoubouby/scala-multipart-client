package com.multipart.client

/** HTTP methods
  */
sealed trait HttpMethod {
  def name: String
}

case object GET extends HttpMethod {
  override def name: String = "GET"
}

case object POST extends HttpMethod {
  override def name: String = "POST"
}

case object PUT extends HttpMethod {
  override def name: String = "PUT"
}

case object DELETE extends HttpMethod {
  override def name: String = "DELETE"
}

case object PATCH extends HttpMethod {
  override def name: String = "PATCH"
}


case object HEAD extends HttpMethod {
  override def name: String = "HEAD"
}

case object OPTIONS extends HttpMethod {
  override def name: String = "OPTIONS"
}

object HttpMethod {
  def fromString(method: String): HttpMethod = method.toUpperCase match {
    case "GET"     => GET
    case "POST"    => POST
    case "PUT"     => PUT
    case "DELETE"  => DELETE
    case "PATCH"   => PATCH
    case "HEAD"    => HEAD
    case "OPTIONS" => OPTIONS
    case _         => throw new IllegalArgumentException(s"Unknown HTTP method: $method")
  }
}
