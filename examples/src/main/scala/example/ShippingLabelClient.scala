package example

import com.multipart.api.Multipart
import com.multipart.client.PlayWSHttpClient
import com.multipart.model.{MultipartPart, MultipartResult}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.libs.json.{Json, JsValue}
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import java.nio.file.{Files, Paths}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Example client for shipping label generation using multipart responses
  *
  * This example demonstrates how to:
  * - Make HTTP requests that return multipart responses
  * - Parse multipart/related responses with JSON metadata and PDF labels
  * - Extract and save different parts (JSON, PDF, images)
  * - Use helper methods to identify content types
  *
  * To run this example:
  * 1. Update the API endpoint and authentication token
  * 2. Run: sbt "examples/runMain example.ShippingLabelClient"
  */
object ShippingLabelClient extends App {

  // ========================================
  // Configuration
  // ========================================

  val apiBaseUrl = sys.env.getOrElse("SHIPPING_API_URL", "https://api.shipping.example.com")
  val apiToken   = sys.env.getOrElse("SHIPPING_API_TOKEN", "your-api-token-here")
  val outputDir  = sys.env.getOrElse("OUTPUT_DIR", "./output")

  // Sample parcel data
  val parcelNumber = "PKG-12345"
  val destination  = "New York, NY"

  // ========================================
  // Setup
  // ========================================

  implicit val system: ActorSystem             = ActorSystem("shipping-label-client")
  implicit val materializer: Materializer      = Materializer(system)
  implicit val ec: ExecutionContext            = system.dispatcher

  // Create output directory
  Files.createDirectories(Paths.get(outputDir))

  println("=" * 50)
  println("Shipping Label Client Example")
  println("=" * 50)
  println()
  println(s"Requesting label for parcel: $parcelNumber")
  println(s"Destination: $destination")
  println()

  // ========================================
  // Create HTTP Client
  // ========================================

  val wsClient: StandaloneAhcWSClient = StandaloneAhcWSClient()
  val httpClient                      = new PlayWSHttpClient(wsClient, baseUrl = apiBaseUrl)

  // ========================================
  // Make Request
  // ========================================

  val requestPayload = Json.obj(
    "parcelNumber" -> parcelNumber,
    "destination"  -> destination,
    "format"       -> "pdf",
    "includeMetadata" -> true,
  )

  val resultFuture: Future[MultipartResult] = Multipart
    .request(httpClient)
    .post("/v1/labels/generate")
    .withAuth(apiToken)
    .withJsonBody(requestPayload)
    .withTimeout(30.seconds)
    .execute()

  // ========================================
  // Process Response
  // ========================================

  val processingFuture = resultFuture.flatMap { result =>
    println(s"✓ Received multipart response")
    println(s"  Format: ${result.metadata.format.name}")
    println(s"  Parts: ${result.parts.size}")
    println()

    // Process each part
    result.parts.foreach { part =>
      processPart(part)
    }

    // Extract and display metadata
    processMetadata(result)

    // Save PDF labels
    savePdfLabels(result)

    // Save images (e.g., barcodes)
    saveImages(result)

    Future.successful(())
  }

  // ========================================
  // Helper Methods
  // ========================================

  def processPart(part: MultipartPart): Unit = {
    println(s"Part: ${part.identifier}")
    println(s"  Content-Type: ${part.contentType.getOrElse("unknown")}")
    println(s"  Size: ${part.sizeFormatted}")

    if (part.filename.isDefined) {
      println(s"  Filename: ${part.filename.get}")
    }

    if (part.contentId.isDefined) {
      println(s"  Content-ID: ${part.contentId.get}")
    }

    // Display content type indicators
    val indicators = Seq(
      if (part.isJson) Some("JSON") else None,
      if (part.isPdf) Some("PDF") else None,
      if (part.isImage) Some(s"Image (${part.imageFormat.getOrElse("unknown")})") else None,
      if (part.isXml) Some("XML") else None,
      if (part.isExcel) Some("Excel") else None,
      if (part.isCsv) Some("CSV") else None,
    ).flatten

    if (indicators.nonEmpty) {
      println(s"  Detected: ${indicators.mkString(", ")}")
    }

    println()
  }

  def processMetadata(result: MultipartResult): Unit = {
    println("=" * 50)
    println("Metadata Parts")
    println("=" * 50)

    result.jsonParts.foreach { part =>
      Try(Json.parse(part.data)) match {
        case Success(json) =>
          println(s"Part: ${part.identifier}")
          println(Json.prettyPrint(json))
          println()

          // Extract specific fields if needed
          (json \ "trackingNumber").asOpt[String].foreach { tracking =>
            println(s"📦 Tracking Number: $tracking")
          }

          (json \ "service").asOpt[String].foreach { service =>
            println(s"🚚 Service: $service")
          }

          println()

        case Failure(ex) =>
          println(s"⚠ Failed to parse JSON in ${part.identifier}: ${ex.getMessage}")
      }
    }
  }

  def savePdfLabels(result: MultipartResult): Unit = {
    println("=" * 50)
    println("Saving PDF Labels")
    println("=" * 50)

    result.pdfParts.foreach { part =>
      val filename = part.filename
        .orElse(part.contentId.map(id => s"${id.replaceAll("[<>]", "")}.pdf"))
        .getOrElse(s"label-$parcelNumber.pdf")

      val outputPath = Paths.get(outputDir, filename)

      try {
        Files.write(outputPath, part.data)
        println(s"✓ Saved: $filename (${part.sizeFormatted})")

        // Validate PDF
        if (part.isValidPdf) {
          println(s"  ✓ Valid PDF format")
        } else {
          println(s"  ⚠ Warning: PDF may be invalid")
        }
      } catch {
        case ex: Exception =>
          println(s"✗ Failed to save $filename: ${ex.getMessage}")
      }
    }

    println()
  }

  def saveImages(result: MultipartResult): Unit = {
    val imageParts = result.imageParts

    if (imageParts.isEmpty) {
      return
    }

    println("=" * 50)
    println("Saving Images")
    println("=" * 50)

    imageParts.foreach { part =>
      val extension = part.imageFormat
        .map(_.toLowerCase)
        .getOrElse(part.contentType.flatMap(_.split("/").lastOption).getOrElse("bin"))

      val filename = part.filename
        .orElse(part.contentId.map(id => s"${id.replaceAll("[<>]", "")}.$extension"))
        .getOrElse(s"image-${System.currentTimeMillis()}.$extension")

      val outputPath = Paths.get(outputDir, filename)

      try {
        Files.write(outputPath, part.data)
        println(
          s"✓ Saved: $filename (${part.sizeFormatted}, ${part.imageFormat.getOrElse("unknown")} format)",
        )
      } catch {
        case ex: Exception =>
          println(s"✗ Failed to save $filename: ${ex.getMessage}")
      }
    }

    println()
  }

  // ========================================
  // Error Handling & Cleanup
  // ========================================

  processingFuture.onComplete {
    case Success(_) =>
      println("=" * 50)
      println("✓ Processing completed successfully")
      println(s"Output directory: $outputDir")
      println("=" * 50)
      cleanup()

    case Failure(ex) =>
      println("=" * 50)
      println(s"✗ Error: ${ex.getMessage}")
      ex.printStackTrace()
      println("=" * 50)
      cleanup()
  }

  private def cleanup(): Unit = {
    Try {
      wsClient.close()
      system.terminate()
    }
  }

  // Wait for completion
  Await.result(system.whenTerminated, 60.seconds)
}
