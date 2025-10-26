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
 * @param rawBody Optional raw response body as bytes
 */
case class NonMultipartResponseException(
  contentType: String,
  status:      Int,
  jsonBody:    Option[JsValue] = None,
  rawBody:     Option[Array[Byte]] = None,
) extends MultipartException(
      s"Not a multipart response. Content-Type: $contentType, Status: $status" +
        jsonBody.fold("")(json => s"\nResponse body: ${Json.prettyPrint(json)}"),
    ) {

  /**
   * Check if this is a JSON error response
   */
  def isJsonError: Boolean = jsonBody.isDefined

  /**
   * Get the raw body as a string if available
   */
  def bodyAsString: Option[String] = rawBody.map(bytes => new String(bytes, "UTF-8"))

  /**
   * Extract a field from JSON body using a path
   *
   * @param path JSON path (e.g., "error", "data.message", "errors[0].detail")
   * @return Option containing the value as JsValue
   */
  def getJsonField(path: String): Option[JsValue] =
    jsonBody.flatMap { json =>
      val pathParts = path.split('.')
      pathParts.foldLeft(Option(json)) {
        case (Some(js), key) => (js \ key).toOption
        case (None, _)       => None
      }
    }

  /**
   * Extract a string field from JSON body
   * Tries common error field names: "error", "message", "errorMessage", "detail"
   */
  def errorMessage: Option[String] =
    jsonBody.flatMap { json =>
      getJsonField("error").flatMap(_.asOpt[String])
        .orElse(getJsonField("message").flatMap(_.asOpt[String]))
        .orElse(getJsonField("errorMessage").flatMap(_.asOpt[String]))
        .orElse(getJsonField("detail").flatMap(_.asOpt[String]))
        .orElse(getJsonField("error_description").flatMap(_.asOpt[String]))
    }

  /**
   * Convert entire JSON body to a flattened map (for logging/debugging)
   * Only converts simple types (String, Number, Boolean)
   */
  def toMap: Map[String, String] =
    jsonBody.map(jsonToMap).getOrElse(Map.empty)

  private def jsonToMap(json: JsValue, prefix: String = ""): Map[String, String] =
    json match {
      case obj: play.api.libs.json.JsObject =>
        obj.fields.flatMap {
          case (key, value: play.api.libs.json.JsObject) =>
            jsonToMap(value, if (prefix.isEmpty) key else s"$prefix.$key")
          case (key, value: play.api.libs.json.JsArray)  =>
            value.value.zipWithIndex.flatMap {
              case (v, i) => jsonToMap(v, if (prefix.isEmpty) s"$key[$i]" else s"$prefix.$key[$i]")
            }
          case (key, value)                              =>
            val fullKey = if (prefix.isEmpty) key else s"$prefix.$key"
            value match {
              case play.api.libs.json.JsString(s)  => Map(fullKey -> s)
              case play.api.libs.json.JsNumber(n)  => Map(fullKey -> n.toString)
              case play.api.libs.json.JsBoolean(b) => Map(fullKey -> b.toString)
              case play.api.libs.json.JsNull       => Map(fullKey -> "null")
              case _                               => Map.empty[String, String]
            }
        }.toMap
      case _                                 => Map.empty
    }
}

/**
 * Exception thrown when multipart parsing fails
 */
case class MultipartParsingException(message: String, cause: Throwable = null)
    extends MultipartException(message, cause)
