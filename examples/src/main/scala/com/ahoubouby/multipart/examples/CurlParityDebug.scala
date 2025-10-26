package com.ahoubouby.multipart.examples

import org.apache.pekko.stream.Materializer
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.libs.ws.JsonBodyWritables._
// import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.json._

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.SystemMaterializer

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object CurlParityDebug extends App {

  implicit val system: ActorSystem = ActorSystem("ws-debug")
  implicit val mat: Materializer = SystemMaterializer(system).materializer
  implicit val ec: ExecutionContext = system.dispatcher

  // ✅ Default client (no custom config headaches)
  val ws: StandaloneAhcWSClient = StandaloneAhcWSClient()(mat)

  val url =
    "https://qualification.colissimo.fr/sls-ws/SlsServiceRest/SlsInternalService/generateLabel"

  val token = "38a0aeb5160ba23cd377c844197eb207"

  // Use the exact JSON you use with curl
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

  val fut = ws
    .url(url)
    .withMethod("POST")
    .withRequestTimeout(30.seconds)
    .withFollowRedirects(true) // like curl
    .withHttpHeaders(
      // 👇 curl-like defaults that often unblock WAFs
      "User-Agent"       -> "curl/8.7.1",
      "Content-Type"     -> "application/json",
      "Accept"           -> "*/*",
      "Accept-Language"  -> "en-US,en;q=0.9",
      "Accept-Encoding"  -> "gzip, deflate, br",
      "token"            -> token
      // If your curl has these, add them too:
      // "Origin"        -> "https://qualification.colissimo.fr",
      // "Referer"       -> "https://qualification.colissimo.fr/",
      // "Cookie"        -> "SESSION=...",   // copy from a successful curl if needed
    )
    .post(payload)
    .map { resp =>
      println(s"STATUS: ${resp.status} ${resp.statusText}")
      println("=== Response Headers ===")
      resp.headers.foreach { case (k, v) => println(s"$k: ${v.mkString(", ")}") }
      if (resp.status >= 400) {
        println("=== Body (first 4KB) ===")
        println(resp.body.take(4096))
      }
    }

  try Await.result(fut, 45.seconds)
  finally {
    ws.close()
    Await.result(system.terminate(), 10.seconds)
    ()
  }
}
