package com.multipart.parser

import play.api.libs.json.{JsValue, Json}

/**
 * Base exception for multipart parsing errors
 */
sealed abstract class MultipartException(message: String, cause: Throwable = null)
    extends Exception(message, cause)

/**
 * Exception thrown when response is not multipart
 *
 * @param contentType The actual Content-Type header value
 * @param status HTTP status code
 * @param jsonBody Optional parsed JSON body if the response was JSON
 */
case class NonMultipartResponseException(
  contentType: String,
  status:      Int,
  jsonBody:    Option[JsValue] = None,
) extends MultipartException(
      s"Not a multipart response. Content-Type: $contentType, Status: $status" +
        jsonBody.fold("")(json => s"\nResponse body: ${Json.prettyPrint(json)}"),
    ) {

  /**
   * Get error message from JSON if available
   */
  def errorMessage: Option[String] =
    jsonBody.flatMap(
      json => (json \ "error").asOpt[String].orElse((json \ "message").asOpt[String]),
    )

  /**
   * Get error details as a map
   */
  def errorDetails: Map[String, String] =
    jsonBody
      .map {
        json =>
          Map(
            "status"    -> (json \ "status").asOpt[Int].map(_.toString),
            "error"     -> (json \ "error").asOpt[String],
            "message"   -> (json \ "message").asOpt[String],
            "path"      -> (json \ "path").asOpt[String],
            "timestamp" -> (json \ "timestamp").asOpt[Long].map(_.toString),
          ).collect {
            case (k, Some(v)) => k -> v
          }
      }
      .getOrElse(Map.empty)

  /**
   * Check if this is a JSON error response
   */
  def isJsonError: Boolean = jsonBody.isDefined
}

/**
 * Exception thrown when multipart parsing fails
 */
case class MultipartParsingException(message: String, cause: Throwable = null)
    extends MultipartException(message, cause)
