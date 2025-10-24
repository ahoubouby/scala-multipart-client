package example

import com.multipart.api.Multipart
import com.multipart.client.PlayWSHttpClient
import com.multipart.model.MultipartResult
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.libs.json.Json
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import java.nio.file.{Files, Paths}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** Example: Shipping Label Client
  *
  * This example demonstrates how to use the Scala Multipart Client library
  * to request and parse multipart responses from a shipping API.
  *
  * Use case: Request a shipping label from an API that returns:
  *   - JSON metadata (tracking number, shipping info)
  *   - PDF label document
  */
object ShippingLabelClient extends App {

  // ========================================
  // Setup Pekko ActorSystem and HTTP Client
  // ========================================

  implicit val system: ActorSystem             = ActorSystem("shipping-labels")
  implicit val mat: Materializer               = Materializer(system)
  implicit val ec: ExecutionContext            = system.dispatcher

  // Create Play WS client
  val wsClient: StandaloneWSClient = StandaloneAhcWSClient()

  // ========================================
  // Configuration
  // ========================================

  val apiBaseUrl = "https://api.shipping-provider.com"
  val apiToken   = sys.env.getOrElse("SHIPPING_API_TOKEN", "your-api-token")

  // ========================================
  // Main Application Logic
  // ========================================

  println("Shipping Label Client Example")
  println("=" * 50)

  // Example: Request shipping label
  val result = requestShippingLabel(
    parcelNumber = "PKG-12345",
    destination  = "New York, NY",
  )

  result.onComplete {
    case Success(multipart) =>
      println("\n✓ Successfully received multipart response")
      processMultipartResponse(multipart)
      shutdown()

    case Failure(exception) =>
      println(s"\n✗ Failed to get shipping label: ${exception.getMessage}")
      exception.printStackTrace()
      shutdown()
  }

  // ========================================
  // API Methods
  // ========================================

  /** Request a shipping label from the API
    *
    * @param parcelNumber The parcel tracking number
    * @param destination The shipping destination
    * @return Future containing the multipart response
    */
  def requestShippingLabel(
    parcelNumber: String,
    destination:  String,
  ): Future[MultipartResult] = {

    println(s"\nRequesting label for parcel: $parcelNumber")
    println(s"Destination: $destination")

    // Create HTTP client with base URL
    val httpClient = new PlayWSHttpClient(wsClient, baseUrl = apiBaseUrl)

    // Build and execute request using fluent API
    Multipart
      .request(httpClient)
      .post("/v1/labels/generate")
      .withAuth(apiToken)
      .withJsonBody(
        Json.obj(
          "parcelNumber" -> parcelNumber,
          "destination"  -> destination,
          "format"       -> "multipart",
        ),
      )
      .withTimeout(30.seconds)
      .withHeader("Accept", "multipart/mixed")
      .execute()
  }

  /** Process the multipart response
    *
    * @param result The parsed multipart result
    */
  def processMultipartResponse(result: MultipartResult): Unit = {
    println(s"\nReceived ${result.parts.size} parts")
    println(s"Format: ${result.metadata.format.name}")
    println("-" * 50)

    // Process JSON metadata
    result.jsonParts.foreach { part =>
      println(s"\n📋 JSON Metadata (${part.identifier})")
      val json = Json.parse(part.data)
      println(Json.prettyPrint(json))

      // Extract tracking number
      (json \ "trackingNumber").asOpt[String].foreach { tracking =>
        println(s"\n✓ Tracking Number: $tracking")
      }
    }

    // Process PDF labels
    result.pdfParts.foreach { part =>
      val filename = s"label-${part.identifier}.pdf"
      savePdfLabel(part.data, filename)
      println(s"\n📄 PDF Label saved: $filename (${part.size} bytes)")
    }

    // Process images (e.g., QR codes)
    result.imageParts.foreach { part =>
      println(s"\n🖼  Image part: ${part.identifier} (${part.contentType.getOrElse("unknown")})")
    }

    // Summary
    println("\n" + "=" * 50)
    println("Summary:")
    println(s"  - JSON parts: ${result.jsonParts.size}")
    println(s"  - PDF parts:  ${result.pdfParts.size}")
    println(s"  - Images:     ${result.imageParts.size}")
    println(s"  - Total:      ${result.parts.size}")
  }

  /** Save PDF label to file
    *
    * @param data The PDF bytes
    * @param filename The output filename
    */
  def savePdfLabel(data: Array[Byte], filename: String): Unit = {
    val path = Paths.get(filename)
    Files.write(path, data)
  }

  /** Shutdown resources
    */
  def shutdown(): Unit = {
    wsClient.close()
    system.terminate()
  }
}
