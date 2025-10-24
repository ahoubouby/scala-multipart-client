package com.multipart.api

import com.multipart.client.HttpClient

/** Entry point for the fluent multipart API
  *
  * This object provides the starting point for building and executing
  * multipart HTTP requests with automatic response parsing.
  *
  * Example usage:
  * {{{
  * val result = Multipart.request(httpClient)
  *   .post("/api/endpoint")
  *   .withAuth("token")
  *   .withJsonBody(Json.obj("key" -> "value"))
  *   .execute()
  * }}}
  */
object Multipart {

  /** Create a new request builder
    *
    * @param httpClient The HTTP client to use for making requests
    * @return A new MultipartRequestBuilder instance
    */
  def request(httpClient: HttpClient): MultipartRequestBuilder =
    new MultipartRequestBuilder(httpClient)
}
