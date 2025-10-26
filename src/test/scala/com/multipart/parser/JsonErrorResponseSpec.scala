package com.multipart.parser

import com.multipart.client.HttpResponse
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

/**
 * Tests for handling JSON error responses when expecting multipart
 */
class JsonErrorResponseSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val system: ActorSystem      = ActorSystem("json-error-spec")
  implicit val mat: Materializer        = Materializer(system)
  implicit val ec: ExecutionContext     = system.dispatcher
  implicit val patience: PatienceConfig = PatienceConfig(Span(5, Seconds), Span(100, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  "MultipartParser" when {

    "receiving JSON error response instead of multipart" should {

      "fail with NonMultipartResponseException containing JSON body" in {
        val jsonError = """{
          "timestamp": 1761436501465,
          "status": 500,
          "error": "Internal Server Error",
          "path": "/sls-ws/SlsServiceRest/SlsInternalService/generateLabel"
        }"""

        val response = new HttpResponse {
          def status: Int                         = 500
          def headers: Map[String, Seq[String]]   = Map(
            "content-type" -> Seq("application/json"),
            "server"       -> Seq("nginx"),
          )
          def bodyAsSource: Source[ByteString, _] = Source.single(ByteString(jsonError))
        }

        val result = MultipartParser.parse(response)

        whenReady(result.failed) { exception =>
          exception shouldBe a[NonMultipartResponseException]
          val ex = exception.asInstanceOf[NonMultipartResponseException]

          ex.status shouldBe 500
          ex.contentType shouldBe "application/json"
          ex.isJsonError shouldBe true
          ex.jsonBody shouldBe defined

          val json = ex.jsonBody.get
          (json \ "status").as[Int] shouldBe 500
          (json \ "error").as[String] shouldBe "Internal Server Error"
          (json \ "path").as[String] shouldBe "/sls-ws/SlsServiceRest/SlsInternalService/generateLabel"
        }
      }

      "extract error message from JSON body" in {
        val jsonError = """{
          "status": 403,
          "error": "Forbidden",
          "message": "Invalid API token"
        }"""

        val response = new HttpResponse {
          def status: Int                         = 403
          def headers: Map[String, Seq[String]]   = Map("content-type" -> Seq("application/json"))
          def bodyAsSource: Source[ByteString, _] = Source.single(ByteString(jsonError))
        }

        val result = MultipartParser.parse(response)

        whenReady(result.failed) { exception =>
          val ex = exception.asInstanceOf[NonMultipartResponseException]
          ex.errorMessage shouldBe Some("Forbidden")
          ex.errorDetails should contain("error" -> "Forbidden")
          ex.errorDetails should contain("message" -> "Invalid API token")
        }
      }

      "handle JSON with message field instead of error field" in {
        val jsonError = """{
          "status": 400,
          "message": "Bad request parameters"
        }"""

        val response = new HttpResponse {
          def status: Int                         = 400
          def headers: Map[String, Seq[String]]   = Map("content-type" -> Seq("application/json"))
          def bodyAsSource: Source[ByteString, _] = Source.single(ByteString(jsonError))
        }

        val result = MultipartParser.parse(response)

        whenReady(result.failed) { exception =>
          val ex = exception.asInstanceOf[NonMultipartResponseException]
          ex.errorMessage shouldBe Some("Bad request parameters")
        }
      }

      "handle malformed JSON gracefully" in {
        val malformedJson = """{"status": 500, "error": "Not valid JSON"""

        val response = new HttpResponse {
          def status: Int                         = 500
          def headers: Map[String, Seq[String]]   = Map("content-type" -> Seq("application/json"))
          def bodyAsSource: Source[ByteString, _] = Source.single(ByteString(malformedJson))
        }

        val result = MultipartParser.parse(response)

        whenReady(result.failed) { exception =>
          val ex = exception.asInstanceOf[NonMultipartResponseException]
          ex.status shouldBe 500
          ex.contentType shouldBe "application/json"
          ex.jsonBody shouldBe None // Malformed JSON should result in None
        }
      }

      "include all error details in errorDetails map" in {
        val jsonError = """{
          "timestamp": 1761436501465,
          "status": 500,
          "error": "Internal Server Error",
          "message": "Database connection failed",
          "path": "/api/endpoint"
        }"""

        val response = new HttpResponse {
          def status: Int                         = 500
          def headers: Map[String, Seq[String]]   = Map("content-type" -> Seq("application/json"))
          def bodyAsSource: Source[ByteString, _] = Source.single(ByteString(jsonError))
        }

        val result = MultipartParser.parse(response)

        whenReady(result.failed) { exception =>
          val ex = exception.asInstanceOf[NonMultipartResponseException]
          val details = ex.errorDetails

          details should contain("status" -> "500")
          details should contain("error" -> "Internal Server Error")
          details should contain("message" -> "Database connection failed")
          details should contain("path" -> "/api/endpoint")
          details should contain("timestamp" -> "1761436501465")
        }
      }

      "handle empty JSON object" in {
        val jsonError = "{}"

        val response = new HttpResponse {
          def status: Int                         = 500
          def headers: Map[String, Seq[String]]   = Map("content-type" -> Seq("application/json"))
          def bodyAsSource: Source[ByteString, _] = Source.single(ByteString(jsonError))
        }

        val result = MultipartParser.parse(response)

        whenReady(result.failed) { exception =>
          val ex = exception.asInstanceOf[NonMultipartResponseException]
          ex.jsonBody shouldBe defined
          ex.errorMessage shouldBe None
          ex.errorDetails shouldBe empty
        }
      }
    }

    "receiving non-JSON, non-multipart response" should {

      "fail with NonMultipartResponseException without JSON body" in {
        val htmlError = "<html><body>Error</body></html>"

        val response = new HttpResponse {
          def status: Int                         = 500
          def headers: Map[String, Seq[String]]   = Map("content-type" -> Seq("text/html"))
          def bodyAsSource: Source[ByteString, _] = Source.single(ByteString(htmlError))
        }

        val result = MultipartParser.parse(response)

        whenReady(result.failed) { exception =>
          exception shouldBe a[NonMultipartResponseException]
          val ex = exception.asInstanceOf[NonMultipartResponseException]

          ex.status shouldBe 500
          ex.contentType shouldBe "text/html"
          ex.isJsonError shouldBe false
          ex.jsonBody shouldBe None
        }
      }

      "fail with NonMultipartResponseException for plain text" in {
        val textError = "Service unavailable"

        val response = new HttpResponse {
          def status: Int                         = 503
          def headers: Map[String, Seq[String]]   = Map("content-type" -> Seq("text/plain"))
          def bodyAsSource: Source[ByteString, _] = Source.single(ByteString(textError))
        }

        val result = MultipartParser.parse(response)

        whenReady(result.failed) { exception =>
          val ex = exception.asInstanceOf[NonMultipartResponseException]
          ex.contentType shouldBe "text/plain"
          ex.jsonBody shouldBe None
        }
      }
    }

    "receiving actual multipart response" should {

      "parse successfully without throwing NonMultipartResponseException" in {
        val boundary = "test-boundary"
        val multipartData = s"""------$boundary\r
                               |Content-Disposition: form-data; name="field1"\r
                               |\r
                               |value1\r
                               |------$boundary--\r
                               |""".stripMargin

        val response = new HttpResponse {
          def status: Int                         = 200
          def headers: Map[String, Seq[String]]   = Map(
            "content-type" -> Seq(s"multipart/form-data; boundary=$boundary"),
          )
          def bodyAsSource: Source[ByteString, _] = Source.single(ByteString(multipartData))
        }

        val result = MultipartParser.parse(response)

        whenReady(result) { multipartResult =>
          multipartResult.parts should not be empty
          multipartResult.metadata.boundary shouldBe boundary
        }
      }
    }
  }

  "NonMultipartResponseException" should {

    "format error message correctly with JSON body" in {
      val json = Json.parse("""{"error": "Test error", "status": 500}""")
      val exception = NonMultipartResponseException(
        contentType = "application/json",
        status = 500,
        jsonBody = Some(json)
      )

      exception.getMessage should include("application/json")
      exception.getMessage should include("Status: 500")
      exception.getMessage should include("Test error")
    }

    "format error message correctly without JSON body" in {
      val exception = NonMultipartResponseException(
        contentType = "text/html",
        status = 404,
        jsonBody = None
      )

      exception.getMessage should include("text/html")
      exception.getMessage should include("Status: 404")
      exception.getMessage should not include("Response body")
    }
  }
}
