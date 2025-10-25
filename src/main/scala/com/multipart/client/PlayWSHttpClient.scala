package com.multipart.client

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest, StandaloneWSResponse}

// Bring Play WS body writers into implicit scope for JsValue, String, ByteString, etc.
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.JsonBodyWritables._

import scala.concurrent.{ExecutionContext, Future}

final class PlayWSHttpClient(
  wsClient: StandaloneWSClient,
  baseUrl:  String = "",
)(implicit ec: ExecutionContext)
    extends HttpClient
    with LazyLogging {

  override def execute(request: HttpRequest): Future[HttpResponse] = {
    val wsRequest = buildRequest(request)

    // Log the outgoing request
    logRequest(request, wsRequest)

    wsRequest.stream().map { response =>
      // Log the incoming response
      logResponse(response)
      new PlayWSHttpResponse(response)
    }
  }

  private def logRequest(request: HttpRequest, wsRequest: StandaloneWSRequest): Unit = {
    val url = joinUrl(baseUrl, request.url)
    logger.info(s"HTTP ${request.method.name} $url")
    logger.debug(s"Request headers:")
    request.headers.foreach { case (k, v) =>
      val displayValue = if (k.toLowerCase.contains("token") || k.toLowerCase.contains("authorization")) {
        v.toString.take(15) + "..."
      } else {
        v.toString
      }
      logger.debug(s"  $k: $displayValue")
    }

    request.body.foreach {
      case JsonBody(json) =>
        logger.debug(s"Request body (JSON): ${json.toString.take(200)}${if (json.toString.length > 200) "..." else ""}")
      case StringBody(str, _) =>
        logger.debug(s"Request body (String): ${str.take(200)}${if (str.length > 200) "..." else ""}")
      case BytesBody(bytes, ct) =>
        logger.debug(s"Request body (Bytes): ${bytes.length} bytes, Content-Type: $ct")
    }
  }

  private def logResponse(response: StandaloneWSResponse): Unit = {
    logger.info(s"HTTP ${response.status} ${response.statusText}")
    logger.debug("Response headers:")
    response.headers.foreach { case (k, v) =>
      logger.debug(s"  $k: ${v.mkString(", ")}")
    }

    val contentType = response.header("Content-Type").getOrElse("unknown")
    logger.info(s"Content-Type: $contentType")

    if (response.status >= 400) {
      logger.warn(s"Non-successful HTTP status: ${response.status} ${response.statusText}")
    }
  }

  private def buildRequest(request: HttpRequest): StandaloneWSRequest = {
    val url  = joinUrl(baseUrl, request.url)
    val base = wsClient.url(url)

    // 1) Apply headers (supports Map[String, String] or Map[String, Seq[String]])
    val withHeaders: StandaloneWSRequest = applyHeaders(base, request.headers)

    // 2) Timeout
    val withTimeout = request.timeout match {
      case Some(t) => withHeaders.withRequestTimeout(t)
      case None    => withHeaders
    }

    // 3) Method
    val withMethod = withTimeout.withMethod(request.method.name)

    // 4) Body + Content-Type (don’t overwrite if caller already set Content-Type)
    setBody(withMethod, request)
  }

  private def joinUrl(base: String, path: String): String =
    if (base.isEmpty) path
    else if (path.isEmpty) base
    else {
      val b = if (base.endsWith("/")) base.dropRight(1) else base
      val p = if (path.startsWith("/")) path.drop(1) else path
      s"$b/$p"
    }

  private def applyHeaders(req: StandaloneWSRequest, headers: Map[String, _]): StandaloneWSRequest =
    headers.foldLeft(req) {
      case (acc, (k, v: Seq[_])) =>
        v.foldLeft(acc) {
          case (inner, vv: String) => inner.withHttpHeaders(k -> vv)
          case (inner, other)      => inner.withHttpHeaders(k -> String.valueOf(other))
        }
      case (acc, (k, v)) =>
        acc.withHttpHeaders(k -> String.valueOf(v))
    }

  private def hasContentType(headers: Map[String, _]): Boolean =
    headers.keys.exists(
      k => k.equalsIgnoreCase("Content-Type"),
    )

  private def setBody(req: StandaloneWSRequest, request: HttpRequest): StandaloneWSRequest =
    request.body match {
      case None =>
        req

      case Some(JsonBody(json)) =>
        val r = req.withBody(json) // needs JsonBodyWritables._
        if (hasContentType(request.headers)) r
        else r.withHttpHeaders("Content-Type" -> "application/json")

      case Some(StringBody(str, ct)) =>
        req.withBody(str).withHttpHeaders("Content-Type" -> ct) // needs DefaultBodyWritables._

      case Some(StringBody(str, null)) =>
        val r = req.withBody(str)
        if (hasContentType(request.headers)) r
        else r.withHttpHeaders("Content-Type" -> "text/plain; charset=UTF-8")

      case Some(BytesBody(bytes, ct)) =>
        req
          .withBody(ByteString(bytes)) // needs DefaultBodyWritables._
          .withHttpHeaders("Content-Type" -> ct) // <- use ct (fixed)

      case Some(BytesBody(bytes, null)) =>
        val r = req.withBody(ByteString(bytes))
        if (hasContentType(request.headers)) r
        else r.withHttpHeaders("Content-Type" -> "application/octet-stream")
    }
}

/** Wrapper for Play WS Response */
final class PlayWSHttpResponse(response: StandaloneWSResponse) extends HttpResponse {
  import scala.collection.immutable.Seq

  override def status: Int = response.status

  override def headers: Map[String, Seq[String]] =
    response.headers.iterator.map {
      case (k, v) => k.toLowerCase -> v.toIndexedSeq
    }.toMap

  override def bodyAsSource: Source[ByteString, _] = response.bodyAsSource
}
