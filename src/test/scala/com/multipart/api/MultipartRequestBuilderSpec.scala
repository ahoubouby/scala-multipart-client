package com.multipart.api

import scala.concurrent.{ExecutionContext, Future}

import com.multipart.client._

import org.apache.pekko.stream.Materializer
import org.scalatest.RecoverMethods.recoverToSucceededIf
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class MultipartRequestBuilderSpec
  extends AnyWordSpec
  with Matchers
  with ScalaFutures {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  implicit val mat: Materializer = org.apache.pekko.stream.SystemMaterializer(
    org.apache.pekko.actor.ActorSystem("test"),
  ).materializer

  "MultipartRequestBuilder" should {

    "add Content-Type for JSON body when absent and preserve when present" in {
      val client = new CapturingFailClient
      val b1     = Multipart.request(client)
        .post("/p")
        .withJsonBody(play.api.libs.json.Json.obj("a" -> 1))
      val _      = recoverToSucceededIf[RuntimeException](b1.execute())

      client.last.get.headers.keySet.map(_.toLowerCase) should contain("content-type")
      client.last.get.headers("Content-Type") shouldBe "application/json"

      val client2 = new CapturingFailClient
      val b2      = Multipart.request(client2)
        .post("/p")
        .withHeader("Content-Type", "application/custom")
        .withJsonBody(play.api.libs.json.Json.obj("a" -> 1))
      val _2      = recoverToSucceededIf[RuntimeException](b2.execute())

      client2.last.get.headers("Content-Type") shouldBe "application/custom"
    }

    "support withQueryParams with encoding and proper concatenation" in {
      val client = new CapturingFailClient
      val b      = Multipart.request(client)
        .get("/search")
        .withQueryParams("q" -> "space here", "lang" -> "fr-FR")
        .withQueryParams("city" -> "Montréal")

      val _ = recoverToSucceededIf[RuntimeException](b.execute())

      client.last.get.url should (
        equal("/search?q=space%20here&lang=fr-FR&city=Montr%C3%A9al") or
          equal("/search?lang=fr-FR&q=space%20here&city=Montr%C3%A9al")
      )
    }
  }

  /** Captures the last request and always fails the Future to avoid invoking MultipartParser */
  final private class CapturingFailClient extends HttpClient {
    var last: Option[HttpRequest] = None

    override def execute(request: HttpRequest): Future[HttpResponse] = {
      last = Some(request)
      Future.failed(new RuntimeException("short-circuit"))
    }

  }
}
