package example

import java.nio.file.{Files, Paths}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import com.multipart.api.Multipart
import com.multipart.client.PlayWSHttpClient
import com.multipart.model.MultipartResult

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.libs.json.Json
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient

/**
 * Example: Shipping Label Client
 *
 * This example demonstrates how to use the Scala Multipart Client library
 * to request and parse multipart responses from a shipping API.
 *
 * Use case: Request a shipping label from an API that returns:
 *   - JSON metadata (tracking number, shipping info)
 *   - PDF label document
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
        "depositDate": "2025-10-24",
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
  val apiToken   = sys.env.getOrElse("SHIPPING_API_TOKEN", "7e13ce23fa232b3fff19480e6fb12c00")
  // Create Play WS client
  val wsClient: StandaloneAhcWSClient = StandaloneAhcWSClient()
  val httpClient                      = new PlayWSHttpClient(wsClient, baseUrl = apiBaseUrl)
  // ========================================
  // Main Application Logic
  // ========================================

  println("Shipping Label Client Example")
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
   * Request a shipping label from the API
   *
   * @param parcelNumber The parcel tracking number
   * @param destination The shipping destination
   * @return Future containing the multipart response
   */
  def requestShippingLabel: Future[MultipartResult] = {

    // Build and execute request using fluent API
    Multipart
      .request(httpClient)
      .post("/sls-ws/SlsServiceRest/SlsInternalService/generateLabel")
      .withHeader("token", apiToken)
      .withJsonBody(payload)
      .withTimeout(30.seconds)
      .execute()
  }

  /**
   * Process the multipart response
   *
   * @param result The parsed multipart result
   */
  def processMultipartResponse(result: MultipartResult): Unit = {
    println(s"\nReceived ${result.parts.size} parts")
    println(s"Format: ${result.metadata.format.name}")
    println("-" * 50)

    // Process JSON metadata
    result.jsonParts.foreach {
      part =>
        println(s"\n📋 JSON Metadata (${part.identifier})")
        val json = Json.parse(part.data)
        println(Json.prettyPrint(json))

        // Extract tracking number
        (json \ "trackingNumber").asOpt[String].foreach {
          tracking =>
            println(s"\n✓ Tracking Number: $tracking")
        }
    }

    // Process PDF labels
    result.pdfParts.foreach {
      part =>
        val filename = s"label-${part.identifier}.pdf"
        savePdfLabel(part.data, filename)
        println(s"\n📄 PDF Label saved: $filename (${part.size} bytes)")
    }

    // Process images (e.g., QR codes)
    result.imageParts.foreach {
      part =>
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

  /**
   * Save PDF label to file
   *
   * @param data The PDF bytes
   * @param filename The output filename
   */
  def savePdfLabel(data: Array[Byte], filename: String): Unit = {
    val path = Paths.get(filename)
    Files.write(path, data)
  }

  /**
   * Shutdown resources
   */
  def shutdown(): Unit = {
    wsClient.close()
    system.terminate()
  }
}
