package com.multipart.api

import com.multipart.client._
import com.multipart.model.MultipartResult
import com.multipart.parser.{MultipartParser, MultipartParserConfig}
import org.apache.pekko.stream.Materializer
import play.api.libs.json.JsValue

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

/** Fluent builder for multipart HTTP requests
  *
  * This builder provides a chainable API for constructing HTTP requests
  * that expect multipart responses. The builder handles:
  *   - HTTP method selection (GET, POST, PUT, DELETE, PATCH)
  *   - Headers and authentication
  *   - Request body (JSON, String, or Bytes)
  *   - Timeouts
  *   - Parser configuration
  *   - Automatic response parsing
  *
  * Example:
  * {{{
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
  * @param headers The request headers
  * @param body The request body
  * @param timeout The request timeout
  * @param parserConfig Optional parser configuration
  */
class MultipartRequestBuilder private[api] (
  httpClient:   HttpClient,
  url:          String                         = "",
  method:       HttpMethod                     = GET,
  headers:      Map[String, String]            = Map.empty,
  body:         Option[RequestBody]            = None,
  timeout:      Option[Duration]               = None,
  parserConfig: Option[MultipartParserConfig]  = None,
) {

  // ===============================
  // HTTP Method Selection
  // ===============================

  /** Set the HTTP method to GET
    *
    * @param path The URL path
    * @return A new builder with GET method
    */
  def get(path: String): MultipartRequestBuilder =
    copy(url = path, method = GET)

  /** Set the HTTP method to POST
    *
    * @param path The URL path
    * @return A new builder with POST method
    */
  def post(path: String): MultipartRequestBuilder =
    copy(url = path, method = POST)

  /** Set the HTTP method to PUT
    *
    * @param path The URL path
    * @return A new builder with PUT method
    */
  def put(path: String): MultipartRequestBuilder =
    copy(url = path, method = PUT)

  /** Set the HTTP method to DELETE
    *
    * @param path The URL path
    * @return A new builder with DELETE method
    */
  def delete(path: String): MultipartRequestBuilder =
    copy(url = path, method = DELETE)

  /** Set the HTTP method to PATCH
    *
    * @param path The URL path
    * @return A new builder with PATCH method
    */
  def patch(path: String): MultipartRequestBuilder =
    copy(url = path, method = PATCH)

  // ===============================
  // Authentication
  // ===============================

  /** Add Authorization header with Bearer token
    *
    * @param token The authentication token
    * @return A new builder with Authorization header
    */
  def withAuth(token: String): MultipartRequestBuilder =
    withBearerAuth(token)

  /** Add Authorization header with Bearer token (explicit)
    *
    * @param token The Bearer token
    * @return A new builder with Authorization header
    */
  def withBearerAuth(token: String): MultipartRequestBuilder =
    withHeader("Authorization", s"Bearer $token")

  /** Add Authorization header with Basic authentication
    *
    * @param username The username
    * @param password The password
    * @return A new builder with Authorization header
    */
  def withBasicAuth(username: String, password: String): MultipartRequestBuilder = {
    val credentials = java.util.Base64.getEncoder.encodeToString(
      s"$username:$password".getBytes("UTF-8"),
    )
    withHeader("Authorization", s"Basic $credentials")
  }

  // ===============================
  // Headers
  // ===============================

  /** Add a single header
    *
    * @param name The header name
    * @param value The header value
    * @return A new builder with the header added
    */
  def withHeader(name: String, value: String): MultipartRequestBuilder =
    copy(headers = headers + (name -> value))

  /** Add multiple headers
    *
    * @param newHeaders The headers to add
    * @return A new builder with all headers added
    */
  def withHeaders(newHeaders: Map[String, String]): MultipartRequestBuilder =
    copy(headers = headers ++ newHeaders)

  /** Add multiple headers from tuples
    *
    * @param newHeaders The headers to add as tuples
    * @return A new builder with all headers added
    */
  def withHeaders(newHeaders: (String, String)*): MultipartRequestBuilder =
    copy(headers = headers ++ newHeaders.toMap)

  // ===============================
  // Request Body
  // ===============================

  /** Set JSON request body
    *
    * The Content-Type will be automatically set to application/json
    *
    * @param json The JSON value
    * @return A new builder with JSON body
    */
  def withJsonBody(json: JsValue): MultipartRequestBuilder =
    copy(body = Some(JsonBody(json)))

  /** Set string request body
    *
    * @param content The string content
    * @param contentType The content type (default: text/plain)
    * @return A new builder with string body
    */
  def withStringBody(content: String, contentType: String = "text/plain"): MultipartRequestBuilder =
    copy(body = Some(StringBody(content, contentType)))

  /** Set bytes request body
    *
    * @param bytes The byte array
    * @param contentType The content type (default: application/octet-stream)
    * @return A new builder with bytes body
    */
  def withBytesBody(bytes: Array[Byte], contentType: String = "application/octet-stream"): MultipartRequestBuilder =
    copy(body = Some(BytesBody(bytes, contentType)))

  // ===============================
  // Timeout
  // ===============================

  /** Set request timeout
    *
    * @param duration The timeout duration
    * @return A new builder with timeout set
    */
  def withTimeout(duration: Duration): MultipartRequestBuilder =
    copy(timeout = Some(duration))

  // ===============================
  // Parser Configuration
  // ===============================

  /** Set custom parser configuration
    *
    * This allows you to customize:
    *   - Boundary (usually auto-detected)
    *   - Max memory buffer size
    *   - Max header size
    *   - Custom classifiers
    *
    * @param config The parser configuration
    * @return A new builder with parser config set
    */
  def withParserConfig(config: MultipartParserConfig): MultipartRequestBuilder =
    copy(parserConfig = Some(config))

  // ===============================
  // Execution
  // ===============================

  /** Execute the request and parse the multipart response
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
    // Build the HTTP request
    println("WS headers: " + headers.map{ case (k,v) => s"$k: ${v}" }.mkString("; "))
    val request = HttpRequest(
      url     = url,
      method  = method,
      headers = headers,
      body    = body,
      timeout = timeout,
    )

    // Execute and parse
    httpClient
      .execute(request)
      .flatMap { response =>
        println("------------ httpClient", response.headers)
        MultipartParser.parse(response, parserConfig)
      }
  }

  // ===============================
  // Internal Copy Method
  // ===============================

  private def copy(
    url:          String                        = this.url,
    method:       HttpMethod                    = this.method,
    headers:      Map[String, String]           = this.headers,
    body:         Option[RequestBody]           = this.body,
    timeout:      Option[Duration]              = this.timeout,
    parserConfig: Option[MultipartParserConfig] = this.parserConfig,
  ): MultipartRequestBuilder =
    new MultipartRequestBuilder(
      httpClient   = this.httpClient,
      url          = url,
      method       = method,
      headers      = headers,
      body         = body,
      timeout      = timeout,
      parserConfig = parserConfig,
    )
}
