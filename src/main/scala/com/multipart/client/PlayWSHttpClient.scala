package com.multipart.client

import scala.concurrent.{ExecutionContext, Future}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest, StandaloneWSResponse}
// Implicit body writers for JsValue, String, ByteString, etc.
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.JsonBodyWritables._

final class PlayWSHttpClient(
  wsClient: StandaloneWSClient,
  baseUrl: String = "",
)(implicit ec: ExecutionContext)
  extends HttpClient {

  override def execute(request: HttpRequest): Future[HttpResponse] = {
    val wsRequest = buildRequest(request)
    wsRequest.stream().map(new PlayWSHttpResponse(_))
  }

  private def buildRequest(request: HttpRequest): StandaloneWSRequest = {
    val url  = joinUrl(baseUrl, request.url)
    val base = wsClient.url(url)
      .withFollowRedirects(true)
    // follow redirects like curl unless caller overrode it
    val base2 = base.withFollowRedirects(true)

    val withHeaders: StandaloneWSRequest = addDefaultsIfMissing(applyHeaders(base2, request.headers))

    val withTimeout = request.timeout match {
      case Some(t) => withHeaders.withRequestTimeout(t)
      case None    => withHeaders
    }

    val withMethod = withTimeout.withMethod(request.method.name)

    setBody(withMethod, request)
  }

  private def addDefaultsIfMissing(req: StandaloneWSRequest): StandaloneWSRequest = {
    def has(h: String) = req.headers.keys.exists(_.equalsIgnoreCase(h))

    var r = req
    if (!has("User-Agent"))
      r = r.withHttpHeaders("User-Agent" -> "curl/8.7.1")
    if (!has("Accept"))
      r = r.withHttpHeaders("Accept" -> "*/*")
    if (!has("Accept-Language"))
      r = r.withHttpHeaders("Accept-Language" -> "en-US,en;q=0.9")
    // Accept-Encoding is handled by AHC, but setting it can help parity:
    if (!has("Accept-Encoding"))
      r = r.withHttpHeaders("Accept-Encoding" -> "gzip, deflate, br")
    r
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
      case (acc, (k, v))         =>
        acc.withHttpHeaders(k -> String.valueOf(v))
    }

  private def hasContentType(headers: Map[String, _]): Boolean =
    headers.keys.exists(_.equalsIgnoreCase("Content-Type"))

  private def setBody(req: StandaloneWSRequest, request: HttpRequest): StandaloneWSRequest =
    request.body match {
      case None =>
        req

      case Some(JsonBody(json)) =>
        val r = req.withBody(json)
        if (hasContentType(request.headers)) r
        else r.withHttpHeaders("Content-Type" -> "application/json")

      case Some(StringBody(str, null)) =>
        val r = req.withBody(str)
        if (hasContentType(request.headers)) r
        else r.withHttpHeaders("Content-Type" -> "text/plain; charset=UTF-8")

      case Some(StringBody(str, ct))    =>
        req.withBody(str).withHttpHeaders("Content-Type" -> ct)

      case Some(BytesBody(bytes, null)) =>
        val r = req.withBody(ByteString(bytes))
        if (hasContentType(request.headers)) r
        else r.withHttpHeaders("Content-Type" -> "application/octet-stream")

      case Some(BytesBody(bytes, ct)) /* if ct != null */ =>
        req.withBody(ByteString(bytes)).withHttpHeaders("Content-Type" -> ct)
    }
}

/** Wrapper for Play WS Response */
final class PlayWSHttpResponse(response: StandaloneWSResponse) extends HttpResponse {
  import scala.collection.immutable.Seq

  override def status: Int = response.status

  override def headers: Map[String, Seq[String]] =
    response.headers.iterator.map { case (k, v) => k.toLowerCase -> v.toIndexedSeq }.toMap

  override def bodyAsSource: Source[ByteString, _] = response.bodyAsSource
}
