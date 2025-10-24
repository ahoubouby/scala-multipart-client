package example

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.Materializer
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn

/** Mock shipping label API server for testing
  *
  * This mock server simulates a shipping label API that returns multipart/related responses
  * containing JSON metadata and PDF label documents.
  *
  * To run:
  * 1. Start this server: sbt "examples/runMain example.MockShippingServer"
  * 2. In another terminal, run the client: sbt "examples/runMain example.ShippingLabelClient"
  */
object MockShippingServer extends App {

  implicit val system: ActorSystem        = ActorSystem("mock-shipping-server")
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext       = system.dispatcher

  val port     = 8080
  val boundary = "boundary123"

  // Sample PDF content (minimal valid PDF)
  val pdfContent: String =
    """%PDF-1.4
      |1 0 obj
      |<<
      |/Type /Catalog
      |/Pages 2 0 R
      |>>
      |endobj
      |2 0 obj
      |<<
      |/Type /Pages
      |/Kids [3 0 R]
      |/Count 1
      |>>
      |endobj
      |3 0 obj
      |<<
      |/Type /Page
      |/Parent 2 0 R
      |/Resources <<
      |/Font <<
      |/F1 <<
      |/Type /Font
      |/Subtype /Type1
      |/BaseFont /Helvetica
      |>>
      |>>
      |>>
      |/MediaBox [0 0 612 792]
      |/Contents 4 0 R
      |>>
      |endobj
      |4 0 obj
      |<<
      |/Length 44
      |>>
      |stream
      |BT
      |/F1 12 Tf
      |100 700 Td
      |(Shipping Label) Tj
      |ET
      |endstream
      |endobj
      |xref
      |0 5
      |0000000000 65535 f
      |0000000009 00000 n
      |0000000058 00000 n
      |0000000115 00000 n
      |0000000315 00000 n
      |trailer
      |<<
      |/Size 5
      |/Root 1 0 R
      |>>
      |startxref
      |407
      |%%EOF
      |""".stripMargin

  val route: Route =
    pathPrefix("v1" / "labels") {
      path("generate") {
        post {
          entity(as[String]) { requestBody =>
            // Generate multipart response
            val jsonMetadata = Json.obj(
              "parcelNumber"   -> "PKG-12345",
              "trackingNumber" -> "1Z999AA10123456784",
              "service"        -> "express",
              "destination" -> Json.obj(
                "address" -> "123 Main St",
                "city"    -> "New York",
                "state"   -> "NY",
                "zipCode" -> "10001",
                "country" -> "USA",
              ),
              "sender" -> Json.obj(
                "name"    -> "ACME Corp",
                "address" -> "456 Business Ave",
                "city"    -> "Los Angeles",
                "state"   -> "CA",
                "zipCode" -> "90001",
                "country" -> "USA",
              ),
              "weight" -> Json.obj(
                "value" -> 2.5,
                "unit"  -> "kg",
              ),
              "dimensions" -> Json.obj(
                "length" -> 30,
                "width"  -> 20,
                "height" -> 15,
                "unit"   -> "cm",
              ),
              "createdAt" -> "2025-10-24T19:39:15Z",
            )

            val multipartBody = createMultipartResponse(Json.stringify(jsonMetadata), pdfContent)

            complete(
              HttpResponse(
                status = StatusCodes.OK,
                headers = Nil,
                entity = HttpEntity(
                  ContentType(
                    MediaTypes.`multipart/related`.withBoundary(boundary),
                  ),
                  multipartBody,
                ),
              ),
            )
          }
        }
      }
    } ~ path("health") {
      get {
        complete(HttpResponse(StatusCodes.OK, entity = "OK"))
      }
    }

  def createMultipartResponse(jsonContent: String, pdfContent: String): String = {
    s"""------$boundary\r
       |Content-ID: <metadata>\r
       |Content-Type: application/json\r
       |\r
       |$jsonContent\r
       |------$boundary\r
       |Content-ID: <label>\r
       |Content-Type: application/pdf\r
       |\r
       |$pdfContent\r
       |------$boundary--\r
       |""".stripMargin
  }

  val bindingFuture: Future[Http.ServerBinding] =
    Http().newServerAt("localhost", port).bind(route)

  bindingFuture.onComplete {
    case scala.util.Success(binding) =>
      println(s"Mock Shipping Server started at http://localhost:$port/")
      println(s"Press RETURN to stop...")
      println()
      println("Available endpoints:")
      println(s"  POST http://localhost:$port/v1/labels/generate")
      println(s"  GET  http://localhost:$port/health")
      println()

    case scala.util.Failure(ex) =>
      println(s"Failed to bind HTTP server: ${ex.getMessage}")
      system.terminate()
  }

  // Wait for user to press return
  StdIn.readLine()

  bindingFuture
    .flatMap(_.unbind())
    .onComplete { _ =>
      println("Server stopped")
      system.terminate()
    }
}
