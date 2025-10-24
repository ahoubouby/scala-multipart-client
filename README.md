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
│   ├── model/              # Core data models
│   │   ├── PartInfo.scala
│   │   ├── MultipartPart.scala
│   │   └── MultipartResult.scala
│   │
│   ├── client/             # HTTP client abstractions
│   │   ├── HttpClient.scala
│   │   └── PlayWSHttpClient.scala
│   │
│   ├── classifier/         # Part classification strategies
│   │   └── PartClassifier.scala
│   │
│   ├── parser/             # Stream-based multipart parser
│   │   ├── MultipartParser.scala
│   │   ├── BodyPartParser.scala
│   │   └── MultipartParserConfig.scala
│   │
│   ├── utils/              # Utilities and helpers
│   │   └── ContentTypeDetector.scala
│   │
│   └── api/                # Fluent API
│       └── Multipart.scala
│
└── src/test/scala/com/multipart/
    └── ... (tests)
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

## Building

```bash
# Compile
sbt compile

# Run tests
sbt test

# Create package
sbt package

# Run with coverage
sbt clean coverage test coverageReport
```

## License

MIT License

## Contributing

Contributions welcome! Please read CONTRIBUTING.md for details.
