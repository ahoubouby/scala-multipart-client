package com.multipart.api

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, _}

import com.multipart.client._
import com.multipart.model.MultipartResult
import com.multipart.parser.{MultipartParser, MultipartParserConfig}

import org.apache.pekko.stream.Materializer
import play.api.libs.json.JsValue

/**
 * Fluent builder for multipart HTTP requests
 *
 * This builder provides a chainable API for constructing HTTP requests
 * that expect multipart responses. The builder handles:
 *   - HTTP method selection (GET, POST, PUT, DELETE, PATCH)
 *   - Headers and authentication
 *   - Request body (JSON, String, or Bytes)
 *   - Timeouts
 *   - Parser configuration
 *   - Automatic response parsing (2xx only)
 *
 * Example:
 * {{{
 * import scala.concurrent.duration._
 * import play.api.libs.json.Json
 *
 * Multipart.request(httpClient)
 *   .post("/api/upload")
 *   .withAuth("my-token")
 *   .withJsonBody(Json.obj("id" -> 123))
 *   .withTimeout(30.seconds)
 *   .execute()
 * }}}
 *
 * @param httpClient The HTTP client to execute requests
 * @param url The URL path for the request
 * @param method The HTTP method (default: GET)
 * @param headers The request headers (case-insensitive checks applied on write)
 * @param body The request body
 * @param timeout The request timeout
 * @param parserConfig Optional parser configuration
 */
class MultipartRequestBuilder private[api] (
  httpClient: HttpClient,
  url: String = "",
  method: HttpMethod = GET,
  headers: Map[String, String] = Map.empty,
  body: Option[RequestBody] = None,
  timeout: Option[Duration] = None,
  parserConfig: Option[MultipartParserConfig] = None) {

  // ===============================
  // HTTP Method Selection
  // ===============================

  def get(path: String):    MultipartRequestBuilder = copy(url = path, method = GET)
  def post(path: String):   MultipartRequestBuilder = copy(url = path, method = POST)
  def put(path: String):    MultipartRequestBuilder = copy(url = path, method = PUT)
  def delete(path: String): MultipartRequestBuilder = copy(url = path, method = DELETE)
  def patch(path: String):  MultipartRequestBuilder = copy(url = path, method = PATCH)

  // ===============================
  // Authentication
  // ===============================

  /** Add Authorization header with Bearer token (alias for withBearerAuth) */
  def withAuth(token: String): MultipartRequestBuilder =
    withBearerAuth(token)

  /** Add Authorization header with Bearer token (explicit) */
  def withBearerAuth(token: String): MultipartRequestBuilder =
    withHeader("Authorization", s"Bearer $token")

  /** Add Authorization header with Basic authentication */
  def withBasicAuth(username: String, password: String): MultipartRequestBuilder = {
    val credentials =
      java.util.Base64.getEncoder.encodeToString(s"$username:$password".getBytes(UTF_8))
    withHeader("Authorization", s"Basic $credentials")
  }

  // ===============================
  // Headers
  // ===============================

  /** Add a single header (case-insensitive replace) */
  def withHeader(name: String, value: String): MultipartRequestBuilder =
    copy(headers = upsertHeader(headers, name, value))

  /** Add multiple headers (case-insensitive replace per key) */
  def withHeaders(newHeaders: Map[String, String]): MultipartRequestBuilder =
    copy(headers = newHeaders.foldLeft(headers) { case (acc, (k, v)) => upsertHeader(acc, k, v) })

  /** Add multiple headers from tuples (case-insensitive replace per key) */
  def withHeaders(newHeaders: (String, String)*): MultipartRequestBuilder =
    withHeaders(newHeaders.toMap)

  /** Remove a header (case-insensitive) */
  def withoutHeader(name: String): MultipartRequestBuilder =
    copy(headers = dropHeader(headers, name))

  // ===============================
  // Request Body
  // ===============================

  /**
   * Set JSON request body
   *
   * Automatically ensures `Content-Type: application/json` unless already set.
   */
  def withJsonBody(json: JsValue): MultipartRequestBuilder =
    copy(
      body = Some(JsonBody(json)),
      headers = ensureContentType(headers, "application/json"),
    )

  /**
   * Set string request body
   *
   * Automatically ensures `Content-Type: <contentType>; charset=UTF-8` unless already set.
   */
  def withStringBody(
    content: String,
    contentType: String = "text/plain",
  ): MultipartRequestBuilder =
    copy(
      body = Some(StringBody(content, s"$contentType; charset=UTF-8")),
      headers = ensureContentType(headers, s"$contentType; charset=UTF-8"),
    )

  /**
   * Set bytes request body
   *
   * Automatically ensures `Content-Type: <contentType>` unless already set.
   */
  def withBytesBody(
    bytes: Array[Byte],
    contentType: String = "application/octet-stream",
  ): MultipartRequestBuilder =
    copy(
      body = Some(BytesBody(bytes, contentType)),
      headers = ensureContentType(headers, contentType),
    )

  // ===============================
  // Query parameters
  // ===============================

  /**
   * Append URL query parameters (properly URL-encoded, idempotent append).
   * If the current `url` already has a `?`, new pairs are appended with `&`.
   * Null values are converted to empty strings.
   *
   * Example:
   *   .withQueryParams("q" -> "space here", "lang" -> "fr")
   *   // adds ?q=space%20here&lang=fr (or &... if query already exists)
   */
  def withQueryParams(params: (String, String)*): MultipartRequestBuilder = {
    if (params.isEmpty) this
    else {
      def enc(s: String): String =
        URLEncoder.encode(if (s == null) "" else s, UTF_8.name()).replace("+", "%20")

      val q   = params.map { case (k, v) => s"${enc(k)}=${enc(v)}" }.mkString("&")
      val sep = if (url.contains("?")) "&" else "?"
      copy(url = s"$url$sep$q")
    }
  }
  // ===============================
  // Timeout
  // ===============================

  def withTimeout(duration: Duration): MultipartRequestBuilder =
    copy(timeout = Some(duration))

  // ===============================
  // Parser Configuration
  // ===============================

  /** Set custom parser configuration (boundary, buffers, classifiers, etc.) */
  def withParserConfig(config: MultipartParserConfig): MultipartRequestBuilder =
    copy(parserConfig = Some(config))

  // ===============================
  // Execution
  // ===============================

  /**
   * Execute the request and parse the multipart response
   *
   * This method:
   *   1. Builds the HTTP request
   *   2. Executes it using the HTTP client
   *   3. Parses the multipart response
   *   4. Returns a structured MultipartResult
   *
   * @param mat Implicit Pekko Materializer for stream processing
   * @param ec Implicit ExecutionContext for async operations
   * @return Future[MultipartResult] containing all parsed parts
   */
  def execute()(implicit mat: Materializer, ec: ExecutionContext): Future[MultipartResult] = {
    val request = HttpRequest(
      url = url,
      method = method,
      headers = headers,
      body = body,
      timeout = timeout,
    )

    // Execute and parse
    httpClient
      .execute(request)
      .flatMap {
        response =>
          MultipartParser.parse(response, parserConfig)
      }
  }

  // ===============================
  // Internal helpers
  // ===============================

  private def copy(
    url: String = this.url,
    method: HttpMethod = this.method,
    headers: Map[String, String] = this.headers,
    body: Option[RequestBody] = this.body,
    timeout: Option[Duration] = this.timeout,
    parserConfig: Option[MultipartParserConfig] = this.parserConfig,
  ): MultipartRequestBuilder =
    new MultipartRequestBuilder(
      httpClient = this.httpClient,
      url = url,
      method = method,
      headers = headers,
      body = body,
      timeout = timeout,
      parserConfig = parserConfig,
    )

  /** Upsert header (case-insensitive key semantics) */
  private def upsertHeader(
    h: Map[String, String],
    name: String,
    value: String,
  ): Map[String, String] =
    dropHeader(h, name) + (name -> value)

  /** Remove header by name (case-insensitive) */
  private def dropHeader(h: Map[String, String], name: String): Map[String, String] =
    h.filterNot { case (k, _) => k.equalsIgnoreCase(name) }

  /** Ensure Content-Type exists; if absent, set to provided value */
  private def ensureContentType(h: Map[String, String], ct: String): Map[String, String] =
    if (h.keys.exists(_.equalsIgnoreCase("Content-Type"))) h
    else h + ("Content-Type" -> ct)
}
