package com.ahoubouby.multipart.examples

import com.multipart.api.Multipart
import com.multipart.client.PlayWSHttpClient
import com.multipart.model.MultipartResult
import play.api.libs.ws.DefaultBodyWritables._  // <-- provides BodyWritable[String]
import play.api.libs.json._
import com.multipart.parser.NonMultipartResponseException

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import java.nio.file.{Files, Paths}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Example: Shipping Label Client for Colissimo API
 *
 * This example demonstrates how to use the Scala Multipart Client library
 * to request and parse multipart responses from the Colissimo shipping API.
 *
 * Use case: Request a shipping label from an API that returns:
 *   - JSON metadata (tracking number, shipping info, or error messages)
 *   - PDF label document (if successful)
 */
object ShippingLabelClient extends App {
  import play.api.libs.json._

  val payload: JsValue = Json.parse(
    """
  {
    "outputFormat": {
      "x": 0,
      "y": 0,
      "outputPrintingType": "PDF_A4_300dpi",
      "dematerialized": true,
      "printCODDocument": false
    },
    "letter": {
      "sender": {
        "address": {
          "companyName": "WSU RECETTE",
          "lastName": "",
          "firstName": "",
          "phoneNumber": "",
          "mobileNumber": "",
          "email": "",
          "line0": "",
          "line1": "",
          "line2": "55 rue d'Arcueil",
          "line3": "",
          "zipCode": "94150",
          "city": "RUNGIS",
          "provinceOuEtatName": null,
          "countryCode": "FR",
          "stateOrProvinceCode": null,
          "doorCode1": "",
          "doorCode2": "",
          "intercom": ""
        },
        "serviceInfo": "",
        "senderParcelRef": null,
        "promotionCode": null
      },
      "addressee": {
        "address": {
          "companyName": null,
          "lastName": "nom 3",
          "firstName": null,
          "phoneNumber": null,
          "mobileNumber": null,
          "email": null,
          "line0": null,
          "line1": null,
          "line2": "adresse 3",
          "line3": null,
          "zipCode": "49000",
          "city": "ANGERS",
          "provinceOuEtatName": null,
          "countryCode": "FR",
          "stateOrProvinceCode": null,
          "doorCode1": null,
          "doorCode2": null,
          "intercom": null
        },
        "serviceInfo": null,
        "addresseeParcelRef": "ref 3",
        "promotionCode": null
      },
      "service": {
        "productCode": "DOM",
        "depositDate": "2025-10-27",
        "totalAmount": 1129,
        "commercialName": "WSU RECETTE",
        "orderNumber": null
      },
      "parcel": {
        "pickupLocationId": null,
        "weight": 3,
        "hazmatFlag": false,
        "hazmatCategory": null,
        "hazmatPrintLogo": false,
        "nonMachinable": false,
        "disabledDeliveryBlockingCode": null,
        "recommendationLevel": null,
        "returnReceipt": null,
        "insuranceValue": null,
        "codamount": null,
        "cod": false,
        "ftd": null,
        "ddp": null,
        "instructions": null
      },
      "codSenderAddress": null,
      "customsDeclarations": {
        "includeCustomsDeclarations": false,
        "numberOfCopies": 4,
        "contents": {
          "article": null,
          "category": null,
          "original": null,
          "explanations": null
        },
        "comments": null,
        "licenceNumber": null,
        "certificatNumber": null,
        "invoiceNumber": null,
        "importerAddress": {
          "companyName": null,
          "lastName": null,
          "firstName": null,
          "city": null,
          "zipCode": null,
          "phoneNumber": null,
          "mobileNumber": null,
          "email": null,
          "line0": null,
          "line1": null,
          "line2": null,
          "line3": null
        },
        "importersReference": null,
        "description": null,
        "stateOrProvinceCode": null
      }
    },
    "fields": {
      "field": [
        { "key": "OUTPUT_PRINT_TYPE_CN23", "value": "PDF_A4_300dpi" },
        { "key": "CUSER_INFO_TEXT_3",      "value": "MANUEL" },
        { "key": "CHECK_CITY",             "value": "false" },
        { "key": "ddp",                    "value": "false" },
        { "key": "PRINT_CUSTOMER_BARCODE", "value": null }
      ],
      "customField": [
        { "key": "IncludeProforma", "value": "0" }
      ]
    }
  }
  """,
  )

  // ========================================
  // Setup Pekko ActorSystem and HTTP Client
  // ========================================

  implicit val system: ActorSystem      = ActorSystem("shipping-labels")
  implicit val mat:    Materializer     = Materializer(system)
  implicit val ec:     ExecutionContext = system.dispatcher

  // ========================================
  // Configuration
  // ========================================

  val apiBaseUrl = "https://qualification.colissimo.fr"
  val apiToken   = sys.env.getOrElse("SHIPPING_API_TOKEN", "b912a29c4177f2a4366d0298a5fa407f")

  // Create Play WS client
  val wsClient: StandaloneAhcWSClient = StandaloneAhcWSClient()
  val httpClient = new PlayWSHttpClient(wsClient, baseUrl = apiBaseUrl)

  // ========================================
  // Main Application Logic
  // ========================================

  println("Colissimo Shipping Label Client")
  println("=" * 50)
  println(s"API Base URL: $apiBaseUrl")
  println(s"API Token: ${apiToken.take(10)}...")
  println("=" * 50)

  // Example: Request shipping label
  val result = requestShippingLabel

  result.onComplete {
    case Success(multipart) =>
      println("\n✓ Successfully received multipart response")
      processMultipartResponse(multipart)
      shutdown()

    case Failure(exception: NonMultipartResponseException) =>
      println(s"\n✗ API returned error response instead of multipart")
      handleJsonError(exception)
      shutdown()

    case Failure(exception) =>
      println(s"\n✗ Failed to get shipping label: ${exception.getMessage}")
      exception.printStackTrace()
      shutdown()
  }

  // ========================================
  // API Methods
  // ========================================

  /**
   * Request a shipping label from the Colissimo API (RAW DEBUG MODE)
   *
   * This version bypasses multipart parsing and just prints the raw response
   * to help diagnose what the server is actually sending.
   *
   * @return Future indicating success/failure
   */
  def requestShippingLabelRaw: Future[Unit] = {
    import org.apache.pekko.stream.scaladsl.Sink

    println("\n📤 Sending request to Colissimo API (RAW DEBUG MODE)...")
    println(s"   Endpoint: POST /sls-ws/SlsServiceRest/SlsInternalService/generateLabel")
    println(s"   Headers:")
    println(s"     - Content-Type: application/json")
    println(s"     - token: ${apiToken.take(10)}...")
    println(s"   Payload size: ${Json.stringify(payload).length} bytes")
    println()

    // Make raw HTTP request without multipart parsing
    val request = wsClient
      .url(s"$apiBaseUrl/sls-ws/SlsServiceRest/SlsInternalService/generateLabel")
      .withRequestTimeout(30.seconds)
      .addHttpHeaders("token" -> apiToken)
      .addHttpHeaders("Content-Type" -> "application/json")
      .post(Json.stringify(payload))

    request.flatMap { response =>
      println("\n" + "=" * 80)
      println("RAW HTTP RESPONSE")
      println("=" * 80)
      println(s"Status Code: ${response.status} ${response.statusText}")
      println(s"\nResponse Headers:")
      response.headers.foreach { case (name, values) =>
        values.foreach { value =>
          println(s"  $name: $value")
        }
      }

      // Consume body as raw bytes
      response.bodyAsSource
        .runFold(org.apache.pekko.util.ByteString.empty)(_ ++ _)
        .map { bytes =>
          val bodyLength = bytes.length
          println(s"\nResponse Body Length: $bodyLength bytes")
          println("\n" + "-" * 80)
          println("RAW RESPONSE BODY:")
          println("-" * 80)

          // Try to display as string
          try {
            val bodyStr = bytes.utf8String

            // Show the full response with control characters visible
            println("\n[First 2000 characters with escaped control chars:]")
            val preview = bodyStr.take(2000)
              .replace("\r", "\\r")
              .replace("\n", "\\n")
            println(preview)

            if (bodyStr.length > 2000) {
              println(s"\n... (${bodyStr.length - 2000} more characters)")
            }

            // Also show formatted version
            println("\n[Actual formatted body - first 1000 chars:]")
            println(bodyStr.take(1000))
            if (bodyStr.length > 1000) {
              println(s"\n... (${bodyStr.length - 1000} more characters)")
            }

            // Show hex dump of first 100 bytes to see exact encoding
            println("\n[Hex dump of first 100 bytes:]")
            val hexBytes = bytes.take(100).toArray
            hexBytes.grouped(16).foreach { group =>
              val hex = group.map(b => f"$b%02x").mkString(" ")
              val ascii = group.map(b => if (b >= 32 && b < 127) b.toChar else '.').mkString
              println(f"$hex%-48s  $ascii")
            }

            // Show hex dump around position 140-180 to see the header delimiter
            if (bytes.length > 180) {
              println("\n[Hex dump of bytes 140-180 (looking for header delimiter \\r\\n\\r\\n = 0d 0a 0d 0a):]")
              val headerHex = bytes.slice(140, 180).toArray
              headerHex.grouped(16).zipWithIndex.foreach { case (group, idx) =>
                val offset = 140 + (idx * 16)
                val hex = group.map(b => f"$b%02x").mkString(" ")
                val ascii = group.map(b => if (b >= 32 && b < 127) b.toChar else '.').mkString
                println(f"$offset%04d: $hex%-48s  $ascii")
              }
            }

            // Search for \r\n\r\n in the data
            val crlfcrlf = org.apache.pekko.util.ByteString("\r\n\r\n")
            val delimiterPos = bytes.indexOfSlice(crlfcrlf)
            println(s"\n[Delimiter Search] Looking for \\r\\n\\r\\n (0d 0a 0d 0a)...")
            if (delimiterPos >= 0) {
              println(s"✓ Found at position: $delimiterPos")
              println(s"  Expected at position 47+headers, checking what's at position 47...")

              // Show what's at position 47 (where parser expects headers to start)
              if (bytes.length > 47) {
                val from47 = bytes.drop(47).take(200).utf8String
                  .replace("\r", "\\r")
                  .replace("\n", "\\n")
                println(s"  Data at position 47: $from47")
              }
            } else {
              println(s"✗ NOT FOUND in the entire ${bytes.length} bytes!")
              println(s"  This explains why the parser is stuck!")
            }


          } catch {
            case e: Exception =>
              println(s"Failed to decode as UTF-8: ${e.getMessage}")
              println("\n[Hex dump of first 200 bytes:]")
              val hexBytes = bytes.take(200).toArray
              hexBytes.grouped(16).foreach { group =>
                val hex = group.map(b => f"$b%02x").mkString(" ")
                println(hex)
              }
          }

          println("\n" + "=" * 80)
          ()
        }
    }
  }

  /**
   * Request a shipping label from the Colissimo API
   *
   * @return Future containing the multipart response
   */
  def requestShippingLabel: Future[MultipartResult] = {
    println("\n📤 Sending request to Colissimo API...")
    println(s"   Endpoint: POST /sls-ws/SlsServiceRest/SlsInternalService/generateLabel")
    println(s"   Headers:")
    println(s"     - Content-Type: application/json")
    println(s"     - token: ${apiToken.take(10)}...")
    println(s"   Payload size: ${Json.stringify(payload).length} bytes")
    println()

    // Custom parser config to handle large PDF labels (up to 1MB)
    val parserConfig = com.multipart.parser.MultipartParserConfig(
      boundary = "",  // Auto-detected from Content-Type header
      maxMemoryBufferSize = 1024 * 1024,  // 1MB (PDF labels are ~80-100KB)
      maxHeaderSize = 4096,  // 4KB (default)
    )

    // Build and execute request using fluent API
    Multipart
      .request(httpClient)
      .post("/sls-ws/SlsServiceRest/SlsInternalService/generateLabel")
      .withHeader("token", apiToken)
      .withJsonBody(payload)
      .withParserConfig(parserConfig)  // ← Add custom config here
      .withTimeout(30.seconds)
      .execute()
      .andThen {
        case Success(value) =>
          println(s"\n📥 Response received successfully")
          println(s"   Multipart format: ${value.metadata.format.name}")
          println(s"   Boundary: ${value.metadata.boundary}")
          println(s"   Number of parts: ${value.parts.size}")

        case Failure(ex) =>
          println(s"\n❌ Request failed: ${ex.getMessage}")
      }
  }

  /**
   * Process the multipart response
   *
   * @param result The parsed multipart result
   */
  def processMultipartResponse(result: MultipartResult): Unit = {
    println("\n" + "=" * 50)
    println("MULTIPART RESPONSE ANALYSIS")
    println("=" * 50)
    println(s"Format: ${result.metadata.format.name}")
    println(s"Boundary: ${result.metadata.boundary}")
    println(s"Total parts: ${result.parts.size}")
    println("-" * 50)

    // Process each part
    result.parts.zipWithIndex.foreach {
      case (part, idx) =>
        println(s"\n📦 Part ${idx + 1}/${result.parts.size}")
        println(s"   Identifier: ${part.identifier}")
        println(s"   Content-Type: ${part.contentType.getOrElse("unknown")}")
        println(s"   Size: ${part.sizeFormatted}")
        println(s"   Type flags: JSON=${part.isJson}, PDF=${part.isPdf}, Image=${part.isImage}")

        if (part.filename.isDefined) {
          println(s"   Filename: ${part.filename.get}")
        }
        if (part.contentId.isDefined) {
          println(s"   Content-ID: ${part.contentId.get}")
        }
    }

    println("\n" + "-" * 50)

    // Process JSON metadata
    result.jsonParts.foreach {
      part =>
        println(s"\n📋 JSON Metadata (${part.identifier})")
        println("-" * 50)
        val json = Json.parse(part.data)
        println(Json.prettyPrint(json))

        // Check for error messages
        (json \ "messages").asOpt[JsArray].foreach {
          messages =>
            println("\n⚠️  API Messages:")
            messages.value.foreach {
              msg =>
                val msgType    = (msg \ "type").asOpt[String].getOrElse("UNKNOWN")
                val msgContent = (msg \ "messageContent").asOpt[String].getOrElse("")
                val msgId      = (msg \ "id").asOpt[String].getOrElse("")
                println(s"   [$msgType] (ID: $msgId) $msgContent")
            }
        }

        // Extract tracking number (if present)
        (json \ "parcelNumber").asOpt[String].foreach {
          parcelNum =>
            if (parcelNum != null && parcelNum.nonEmpty) {
              println(s"\n✓ Parcel Number: $parcelNum")
            }
        }

        (json \ "trackingNumber").asOpt[String].foreach {
          tracking =>
            if (tracking != null && tracking.nonEmpty) {
              println(s"✓ Tracking Number: $tracking")
            }
        }
    }

    // Process PDF labels
    if (result.pdfParts.nonEmpty) {
      println("\n" + "-" * 50)
      result.pdfParts.foreach {
        part =>
          val filename = s"label-${System.currentTimeMillis()}.pdf"
          savePdfLabel(part.data, filename)
          println(s"\n📄 PDF Label saved: $filename")
          println(s"   Size: ${part.sizeFormatted}")
          println(s"   Valid PDF: ${part.isValidPdf}")
      }
    } else {
      println("\n⚠️  No PDF labels in response")
    }

    // Process images (e.g., QR codes, barcodes)
    if (result.imageParts.nonEmpty) {
      println("\n" + "-" * 50)
      result.imageParts.foreach {
        part =>
          println(s"\n🖼  Image part: ${part.identifier}")
          println(s"   Type: ${part.contentType.getOrElse("unknown")}")
          println(s"   Format: ${part.imageFormat.getOrElse("unknown")}")
          println(s"   Size: ${part.sizeFormatted}")
      }
    }

    // Summary
    println("\n" + "=" * 50)
    println("SUMMARY")
    println("=" * 50)
    println(s"  JSON parts:  ${result.jsonParts.size}")
    println(s"  PDF parts:   ${result.pdfParts.size}")
    println(s"  Image parts: ${result.imageParts.size}")
    println(s"  Total parts: ${result.parts.size}")
    println("=" * 50)
  }

  /**
   * Save PDF label to file
   *
   * @param data The PDF bytes
   * @param filename The output filename
   */
  def savePdfLabel(data: Array[Byte], filename: String): Unit = {
    val path = Paths.get(filename)
    Files.write(path, data)
    ()
  }

  /**
   * Handle JSON error response
   *
   * @param exception The NonMultipartResponseException containing error details
   */
  def handleJsonError(exception: NonMultipartResponseException): Unit = {
    println("\n" + "=" * 50)
    println("ERROR RESPONSE DETAILS")
    println("=" * 50)
    println(s"HTTP Status: ${exception.status}")
    println(s"Content-Type: ${exception.contentType}")

    if (exception.isJsonError) {
      println("\nJSON Error Body:")
      println("-" * 50)

      // Access raw JsValue for full flexibility
      exception.jsonBody.foreach { json =>
        println(Json.prettyPrint(json))
      }

      // Use built-in helper for common error messages
      exception.errorMessage.foreach { msg =>
        println(s"\nError Message: $msg")
      }

      // Example: Extract specific fields using getJsonField
      exception.getJsonField("timestamp").foreach { ts =>
        println(s"Timestamp: $ts")
      }

      // Example: Convert entire JSON to flat map for logging
      val allFields = exception.toMap
      if (allFields.nonEmpty) {
        println("\nAll Fields:")
        allFields.foreach {
          case (key, value) => println(s"  $key: $value")
        }
      }

      // Example: Custom field extraction for your specific API
      exception.jsonBody.foreach { json =>
        // For Colissimo-specific fields (customize for your API)
        (json \ "path").asOpt[String].foreach(path => println(s"Request Path: $path"))
        (json \ "status").asOpt[Int].foreach(status => println(s"Error Status: $status"))
      }
    } else {
      println("\nNon-JSON error response received")
      exception.bodyAsString.foreach { body =>
        println(s"Raw body: ${body.take(500)}${if (body.length > 500) "..." else ""}")
      }
    }
    println("=" * 50)
  }

  /**
   * Shutdown resources
   */
  def shutdown(): Unit = {
    wsClient.close()
    system.terminate()
    println("\n🔚 Shutting down...")
  }
}
