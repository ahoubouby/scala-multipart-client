# Shipping Labels Example

This example demonstrates how to use the Scala Multipart Client library to interact with a shipping API that returns multipart responses containing both JSON metadata and PDF label documents.

## Overview

The example shows:
- Setting up the library in a real project
- Making HTTP requests with the fluent API
- Parsing multipart responses
- Extracting and processing different part types (JSON, PDF, images)
- Error handling
- Resource management

## Prerequisites

- Scala 2.13.16+
- SBT 1.9.8+
- Shipping API token (for actual API calls)

## Installation

```bash
cd examples/shipping-labels
sbt compile
```

## Configuration

Set your API token:

```bash
export SHIPPING_API_TOKEN=your_api_token_here
```

Or edit `ShippingLabelClient.scala` and replace the placeholder.

## Running the Example

```bash
sbt run
```

## Code Walkthrough

### 1. Setup Dependencies

```scala
libraryDependencies ++= Seq(
  "io.github.ahoubouby" %% "scala-multipart-client" % "0.1.0",
  "org.playframework" %% "play-ws-standalone" % "3.0.4",
  "org.apache.pekko" %% "pekko-stream" % "1.0.2"
)
```

### 2. Initialize HTTP Client

```scala
implicit val system: ActorSystem = ActorSystem("shipping-labels")
implicit val mat: Materializer = Materializer(system)
implicit val ec: ExecutionContext = system.dispatcher

val wsClient: StandaloneWSClient = StandaloneAhcWSClient()
val httpClient = new PlayWSHttpClient(wsClient, baseUrl = apiBaseUrl)
```

### 3. Make Request

```scala
val result = Multipart.request(httpClient)
  .post("/v1/labels/generate")
  .withAuth(apiToken)
  .withJsonBody(Json.obj(
    "parcelNumber" -> "PKG-12345",
    "destination" -> "New York, NY"
  ))
  .withTimeout(30.seconds)
  .execute()
```

### 4. Process Response

```scala
result.map { multipart =>
  // Extract JSON metadata
  multipart.jsonParts.foreach { part =>
    val json = Json.parse(part.data)
    println(Json.prettyPrint(json))
  }

  // Save PDF labels
  multipart.pdfParts.foreach { part =>
    Files.write(Paths.get(s"label.pdf"), part.data)
  }

  // Process images
  multipart.imageParts.foreach { part =>
    println(s"Image: ${part.identifier}")
  }
}
```

## Expected Output

```
Shipping Label Client Example
==================================================

Requesting label for parcel: PKG-12345
Destination: New York, NY

✓ Successfully received multipart response

Received 3 parts
Format: multipart/mixed
--------------------------------------------------

📋 JSON Metadata (metadata)
{
  "trackingNumber": "1Z999AA10123456784",
  "carrier": "UPS",
  "service": "Ground"
}

✓ Tracking Number: 1Z999AA10123456784

📄 PDF Label saved: label-shipping-label.pdf (25643 bytes)

🖼  Image part: qr-code (image/png)

==================================================
Summary:
  - JSON parts: 1
  - PDF parts:  1
  - Images:     1
  - Total:      3
```

## Key Features Demonstrated

### Fluent API
- Method chaining for building requests
- Authentication with `.withAuth()`
- JSON body with `.withJsonBody()`
- Timeout configuration with `.withTimeout()`

### Content Type Detection
- Automatic detection of JSON parts
- PDF detection via magic bytes or content-type
- Image type detection

### Pattern Matching
- Filter parts by type (JSON, PDF, images)
- Access parts by identifier
- Use predicates for custom filtering

### Error Handling
```scala
result.onComplete {
  case Success(multipart) => processResponse(multipart)
  case Failure(exception) => handleError(exception)
}
```

## Testing with Mock API

For testing without a real API:

```scala
// Create a mock HTTP response
class MockShippingApi extends HttpClient {
  override def execute(request: HttpRequest): Future[HttpResponse] = {
    Future.successful(new MockHttpResponse(
      status = 200,
      headers = Map(
        "content-type" -> Seq("multipart/mixed; boundary=test123")
      ),
      body = createMockMultipartBody()
    ))
  }
}
```

## Further Reading

- [Library Documentation](../../README.md)
- [API Reference](../../ARCHITECTURE.md)
- [Publishing Guide](../../PUBLISHING.md)

## License

MIT License - Same as the main library
