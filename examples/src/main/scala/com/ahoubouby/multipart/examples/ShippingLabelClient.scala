package com.ahoubouby.multipart.examples

import com.multipart.api.Multipart
import com.multipart.client.PlayWSHttpClient
import com.multipart.model.MultipartResult
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
  val apiToken   = sys.env.getOrElse("SHIPPING_API_TOKEN", "38a0aeb5160ba23cd377c844197eb207")

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

    case Failure(exception) =>
      println(s"\n✗ Failed to get shipping label: ${exception.getMessage}")
      exception.printStackTrace()
      shutdown()
  }

  // ========================================
  // API Methods
  // ========================================

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

    // Build and execute request using fluent API
    Multipart
      .request(httpClient)
      .post("/sls-ws/SlsServiceRest/SlsInternalService/generateLabel")
      .withHeader("token", apiToken)
      // """multipart/related; type="application/json""""
      .withHeader("Accept", """multipart/related; type="application/json"""")
      .withHeader("User-Agent", "curl/8.5.0")
      .withJsonBody(payload)

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
   * Shutdown resources
   */
  def shutdown(): Unit = {
    wsClient.close()
    system.terminate()
    println("\n🔚 Shutting down...")
  }
}
