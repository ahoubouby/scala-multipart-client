# Scala Multipart Client

A generic, type-safe Scala library for parsing multipart HTTP responses with support for `multipart/form-data`, `multipart/related`, and `multipart/mixed` formats.

## Features

✅ **Generic** - Works with any multipart format (form-data, related, mixed)
✅ **Type-Safe** - Full Scala type safety with sealed traits
✅ **Pluggable** - Extensible classifier system for custom formats
✅ **Streaming** - Memory-efficient Pekko Streams-based parsing
✅ **Framework Agnostic** - HTTP client abstraction (Play WS included)
✅ **Content Detection** - Automatic detection of JSON, PDF, images, etc.
✅ **Pattern Matching** - Find parts by regex patterns or content types

## Requirements

- Scala 2.13.16
- Play Framework 3.0.4 (with Pekko)
- SBT 1.9.8+

## Project Structure

```
scala-multipart-client/
├── src/main/scala/com/multipart/
│   ├── api/                   # Fluent API layer
│   │   ├── Multipart.scala
│   │   └── MultipartRequestBuilder.scala
│   │
│   ├── client/                # HTTP client abstractions
│   │   ├── HttpClient.scala
│   │   ├── HttpMethod.scala
│   │   ├── HttpRequest.scala
│   │   ├── HttpResponse.scala
│   │   ├── PartInfo.scala
│   │   └── PlayWSHttpClient.scala
│   │
│   ├── classifier/            # Part classification strategies
│   │   ├── PartClassifier.scala
│   │   ├── FormDataClassifier.scala
│   │   ├── RelatedClassifier.scala
│   │   ├── MixedClassifier.scala
│   │   ├── UnknownClassifier.scala
│   │   └── ChainedClassifier.scala
│   │
│   ├── model/                 # Core data models
│   │   ├── MultipartPart.scala
│   │   └── MultipartResult.scala
│   │
│   ├── parser/                # Stream-based multipart parser
│   │   ├── MultipartParser.scala
│   │   ├── GenericBodyPartParser.scala
│   │   ├── FormatDetector.scala
│   │   ├── MultipartParserConfig.scala
│   │   └── Part.scala
│   │
│   └── utils/                 # Utilities and helpers
│       ├── BoyerMoore.scala
│       └── ContentTypeDetector.scala
│
└── src/test/scala/com/multipart/  # Comprehensive test suite
    ├── TestFixtures.scala
    ├── utils/
    ├── classifier/
    ├── model/
    ├── parser/
    ├── api/
    └── integration/
```

## Quick Start

### 1. Add Dependency

```scala
libraryDependencies += "com.multipart" %% "scala-multipart-client" % "0.1.0"
```

### 2. Basic Usage

```scala
import com.multipart.api.Multipart
import com.multipart.client.PlayWSHttpClient
import play.api.libs.json.Json

// Create HTTP client
implicit val wsClient: StandaloneWSClient = ...
val httpClient = new PlayWSHttpClient(wsClient, baseUrl = "https://api.example.com")

// Make request and parse multipart response
val result: Future[MultipartResult] = Multipart.request(httpClient)
  .post("/api/generate-label")
  .withAuth("your-token")
  .withJsonBody(Json.obj("parcelNumber" -> "123456"))
  .execute()

// Use the result
result.map { multipart =>
  // Find JSON parts
  val jsonParts = multipart.jsonParts
  
  // Find PDF parts
  val pdfParts = multipart.pdfParts
  
  // Find by pattern
  val labels = multipart.getPartsMatching(".*label.*")
  
  println(s"Found ${multipart.parts.size} parts")
}
```

### 3. Working with Parts

```scala
result.map { multipart =>
  multipart.parts.foreach { part =>
    println(s"Part: ${part.identifier}")
    println(s"  Type: ${part.contentType}")
    println(s"  Size: ${part.size} bytes")
    
    if (part.isPdf) {
      // Save PDF
      Files.write(Paths.get(s"${part.identifier}.pdf"), part.data)
    } else if (part.isJson) {
      // Parse JSON
      val json = Json.parse(part.data)
      processJson(json)
    }
  }
}
```

## Supported Formats

### multipart/form-data (RFC 7578)
```http
Content-Type: multipart/form-data; boundary=boundary123

--boundary123
Content-Disposition: form-data; name="field1"

value1
--boundary123
Content-Disposition: form-data; name="file"; filename="doc.pdf"
Content-Type: application/pdf

[PDF bytes]
--boundary123--
```

### multipart/related (RFC 2387)
```http
Content-Type: multipart/related; boundary=boundary123; start="<root>"

--boundary123
Content-ID: <root>
Content-Type: application/json

{"id": "123"}
--boundary123
Content-ID: <attachment>
Content-Type: application/pdf

[PDF bytes]
--boundary123--
```

### multipart/mixed (RFC 2046)
```http
Content-Type: multipart/mixed; boundary=boundary123

--boundary123
Content-ID: <part1>
Content-Type: text/plain

Text content
--boundary123
Content-Location: /path/to/resource
Content-Type: image/png

[PNG bytes]
--boundary123--
```

## Advanced Features

### Custom Classifiers

```scala
import com.multipart.classifier.PartClassifier

object MyApiClassifier extends PartClassifier {
  def classify(headers: Map[String, String]): Option[PartInfo] = {
    headers.get("x-custom-header").map { value =>
      CustomPartInfo(value, headers.get("content-type"))
    }
  }
}

// Use custom classifier
val config = MultipartParserConfig(
  boundary = "",  // Auto-detected
  classifiers = Seq(MyApiClassifier, ChainedClassifier.default)
)

Multipart.request(httpClient)
  .withParserConfig(config)
  .execute()
```

### Pattern-Based Access

```scala
// Find all labels
val labels = result.getPartsMatching(".*label.*")

// Find all parts with specific content type
val pdfs = result.getPartsByContentType("pdf")

// Find using predicate
val jsonMetadata = result.getPartsByType {
  case info: RelatedPartInfo if info.contentType.exists(_.contains("json")) => true
  case _ => false
}
```

## Testing

The library includes a comprehensive test suite with 140+ test cases covering all components.

### Running Tests

```bash
# Run all tests
sbt test

# Run specific test suite
sbt "testOnly com.multipart.utils.BoyerMooreSpec"

# Run tests with coverage
sbt clean coverage test coverageReport

# Run tests continuously
sbt ~test
```

### Test Structure

```
src/test/scala/com/multipart/
├── TestFixtures.scala           # Common test data and helpers
├── utils/
│   ├── BoyerMooreSpec.scala           # Boyer-Moore algorithm tests
│   └── ContentTypeDetectorSpec.scala  # Content detection tests
├── classifier/
│   └── ClassifierSpec.scala           # All classifier tests
├── model/
│   ├── MultipartPartSpec.scala        # MultipartPart tests
│   └── MultipartResultSpec.scala      # MultipartResult tests
├── parser/
│   └── FormatDetectorSpec.scala       # Format detection tests
├── api/
│   └── (API layer tests)
└── integration/
    └── (End-to-end tests)
```

### Test Coverage

- **Utils**: BoyerMoore algorithm, content type detection
- **Classifiers**: FormData, Related, Mixed, Unknown, Chained
- **Model**: MultipartPart, MultipartResult, format detection
- **Parser**: FormatDetector, boundary extraction, config generation
- **Integration**: End-to-end multipart parsing scenarios

### Writing Tests

Use the provided `TestFixtures` for common test data:

```scala
import com.multipart.TestFixtures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MySpec extends AnyWordSpec with Matchers {
  "MyComponent" should {
    "handle form-data" in {
      val content = TestFixtures.simpleFormData()
      val boundary = TestFixtures.simpleBoundary
      // Your test here
    }
  }
}
```

## Building

```bash
# Compile
sbt compile

# Run tests
sbt test

# Create package
sbt package

# Format code
sbt scalafmt

# Run with coverage
sbt clean coverage test coverageReport
```

## Examples

### Complete Example: Shipping Label Generation

```scala
import com.multipart.api.Multipart
import com.multipart.client.PlayWSHttpClient
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.libs.json.Json
import play.api.libs.ws.StandaloneWSClient

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object ShippingLabelExample {
  def generateLabel(
    parcelNumber: String,
    wsClient: StandaloneWSClient
  )(implicit
    mat: Materializer,
    ec: ExecutionContext
  ): Future[Unit] = {

    // Create HTTP client
    val httpClient = new PlayWSHttpClient(
      wsClient,
      baseUrl = "https://api.shipping.com"
    )

    // Make request
    val result = Multipart.request(httpClient)
      .post("/v1/labels/generate")
      .withAuth("your-api-token")
      .withJsonBody(Json.obj(
        "parcelNumber" -> parcelNumber,
        "format" -> "pdf"
      ))
      .withTimeout(30.seconds)
      .execute()

    // Process response
    result.map { multipart =>
      println(s"Received ${multipart.parts.size} parts")

      // Extract metadata
      multipart.jsonParts.foreach { part =>
        val json = Json.parse(part.data)
        println(s"Metadata: $json")
      }

      // Save label PDF
      multipart.pdfParts.foreach { part =>
        val filename = s"label-$parcelNumber.pdf"
        Files.write(Paths.get(filename), part.data)
        println(s"Saved: $filename (${part.size} bytes)")
      }
    }
  }
}
```

### Example: Custom Classifier

```scala
import com.multipart.classifier.PartClassifier
import com.multipart.client.RelatedPartInfo

// Custom classifier for your API
object MyApiClassifier extends PartClassifier {
  def classify(headers: Map[String, String]): Option[PartInfo] = {
    // Check for custom header
    headers.get("x-part-id").map { partId =>
      RelatedPartInfo(
        contentId = s"<$partId>",
        contentType = headers.get("content-type"),
        contentLocation = headers.get("x-part-location")
      )
    }
  }
}

// Use it
val config = MultipartParserConfig(
  boundary = "",  // Auto-detected
  classifiers = Seq(
    MyApiClassifier,
    FormDataClassifier,
    RelatedClassifier,
    UnknownClassifier
  )
)

Multipart.request(httpClient)
  .get("/api/data")
  .withParserConfig(config)
  .execute()
```

### Example: Error Handling

```scala
Multipart.request(httpClient)
  .post("/api/endpoint")
  .withAuth(token)
  .withJsonBody(payload)
  .execute()
  .map { result =>
    // Success
    println(s"Processed ${result.parts.size} parts")
    result
  }
  .recover {
    case ex: Exception =>
      logger.error("Failed to parse multipart response", ex)
      // Handle error
      MultipartResult(Seq.empty, metadata)
  }
```

## License

MIT License

## Contributing

Contributions welcome! Please read CONTRIBUTING.md for details.
