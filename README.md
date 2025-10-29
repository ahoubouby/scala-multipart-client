# Scala Multipart Client

[![Maven Central](https://img.shields.io/maven-central/v/io.github.ahoubouby/scala-multipart-client_2.13.svg?label=Maven%20Central&color=blue)](https://search.maven.org/search?q=g:%22io.github.ahoubouby%22%20AND%20a:%22scala-multipart-client_2.13%22)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Scala Version](https://img.shields.io/badge/scala-2.13.16-red.svg)](https://www.scala-lang.org/download/2.13.16.html)
[![Test Coverage](https://img.shields.io/badge/coverage-70%25-orange.svg)](#test-coverage)
[![GitHub Issues](https://img.shields.io/github/issues/ahoubouby/scala-multipart-client.svg)](https://github.com/ahoubouby/scala-multipart-client/issues)
[![GitHub Stars](https://img.shields.io/github/stars/ahoubouby/scala-multipart-client.svg?style=social)](https://github.com/ahoubouby/scala-multipart-client)

A generic, type-safe Scala library for parsing multipart HTTP responses with support for `multipart/form-data`, `multipart/related`, and `multipart/mixed` formats.

---

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Supported Formats](#supported-formats)
- [Advanced Features](#advanced-features)
- [Testing](#testing)
- [Building](#building)
- [Examples](#examples)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

---

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

## Installation

### Maven Central (Recommended)

Add to your `build.sbt`:

```scala
libraryDependencies += "io.github.ahoubouby" %% "scala-multipart-client" % "0.1.0"
```

### GitHub Packages

Add to your `build.sbt`:

```scala
resolvers += "GitHub Package Registry" at "https://maven.pkg.github.com/ahoubouby/scala-multipart-client"

credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "YOUR_GITHUB_USERNAME",
  "YOUR_GITHUB_TOKEN"  // Personal access token with read:packages
)

libraryDependencies += "io.github.ahoubouby" %% "scala-multipart-client" % "0.1.0"
```

### Mill

```scala
def ivyDeps = Agg(
  ivy"io.github.ahoubouby::scala-multipart-client:0.1.0"
)
```

### Maven

```xml
<dependency>
    <groupId>io.github.ahoubouby</groupId>
    <artifactId>scala-multipart-client_2.13</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle

```gradle
dependencies {
    implementation 'io.github.ahoubouby:scala-multipart-client_2.13:0.1.0'
}
```

## Quick Start

### 1. Setup Dependencies

Ensure your project includes the required dependencies (they're automatically included as transitive dependencies):

- Scala 2.13.16
- Play Framework 3.0.4 (with Pekko)
- Pekko Streams 1.0.2

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

# Check coverage threshold
sbt coverage test coverageReport
# Open: target/scala-2.13/scoverage-report/index.html
```

### Test Coverage

[![Test Coverage](https://img.shields.io/badge/coverage-70%25-orange.svg)](#test-coverage)

Current coverage: **~70%** (Target: **85%+**)

| Component | Coverage | Status |
|-----------|----------|--------|
| Utils (BoyerMoore, ContentType) | 80%+ | ✅ Good |
| Classifiers | 70%+ | ⚠️ Needs improvement |
| Model | 50%+ | ⚠️ Needs improvement |
| Parser | 60%+ | ⚠️ Needs improvement |
| API | 20%+ | ❌ Critical - needs tests |
| Client | 0% | ❌ Critical - needs tests |

**Coverage Goals:**
- Minimum statement coverage: 70%
- Minimum branch coverage: 60%
- Target overall coverage: 85%+

See [IMPROVEMENT_PLAN.md](docs/IMPROVEMENT_PLAN.md) for detailed testing strategy.

### Test Structure

```
src/test/scala/com/multipart/
├── TestFixtures.scala                  # Common test data and helpers
├── utils/
│   ├── BoyerMooreSpec.scala           # Boyer-Moore algorithm tests
│   └── ContentTypeDetectorSpec.scala  # Content detection tests
├── classifier/
│   └── ClassifierSpec.scala           # All classifier tests
├── model/
│   ├── MultipartPartSpec.scala        # MultipartPart tests
│   └── MultipartResultSpec.scala      # MultipartResult tests
├── parser/
│   ├── FormatDetectorSpec.scala       # Format detection tests
│   ├── JsonErrorResponseSpec.scala    # Error handling tests
│   └── IncompleteMultipartSpec.scala  # Edge case tests
├── api/
│   └── MultipartRequestBuilderSpec.scala  # API layer tests
└── integration/
    └── (End-to-end tests - coming soon)
```

### Test Categories

**Unit Tests (Current):**
- ✅ Boyer-Moore algorithm
- ✅ Content type detection
- ✅ All classifiers (FormData, Related, Mixed, Unknown, Chained)
- ✅ MultipartPart and MultipartResult
- ✅ Format detection and boundary extraction
- ✅ JSON error response handling
- ✅ Incomplete multipart responses
- ⚠️ MultipartRequestBuilder (basic tests only)

**Integration Tests (Planned):**
- ❌ Real API integration tests
- ❌ Large file handling tests
- ❌ Concurrent request tests
- ❌ Error handling scenarios

**Performance Tests (Planned):**
- ❌ Benchmark suite
- ❌ Memory profiling tests
- ❌ Throughput tests

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

## Documentation

### 📚 User Guides
- [Quick Start Guide](docs/getting-started.md) _(coming soon)_
- [Best Practices](docs/best-practices.md) _(coming soon)_
- [Performance Tuning](docs/performance-tuning.md) _(coming soon)_
- [Error Handling Guide](docs/error-handling.md) _(coming soon)_

### 📖 Reference Documentation
- [Architecture Overview](docs/ARCHITECTURE.md) - System design and data flow
- [Troubleshooting Guide](docs/TROUBLESHOOTING.md) - Common issues and solutions
- [Parsing Issues Analysis](docs/PARSING_ISSUES_ANALYSIS.md) - Deep dive into parsing
- [JSON Error Handling](docs/JSON_ERROR_HANDLING.md) - Error response handling
- [Improvement Plan](docs/IMPROVEMENT_PLAN.md) - Roadmap and future enhancements
- [Publishing Guide](docs/PUBLISHING.md) - Release process

### 🔧 API Documentation
- [Scaladoc](https://ahoubouby.github.io/scala-multipart-client/api/) _(coming soon)_

### 💡 Examples
- [Shipping Label Client](examples/src/main/scala/com/ahoubouby/multipart/examples/ShippingLabelClient.scala) - Complete real-world example

### 🎯 Roadmap

**Current Version: 0.1.0**

See [IMPROVEMENT_PLAN.md](docs/IMPROVEMENT_PLAN.md) for detailed roadmap:
- **Phase 1 (v0.2.0):** Stability & Testing - 85%+ test coverage
- **Phase 2 (v0.3.0):** Performance & Observability - Metrics, benchmarks
- **Phase 3 (v0.4.0):** Features - HTTP/2, OAuth, streaming
- **Phase 4 (v1.0.0):** Production-ready release

---

## Contributing

Contributions are welcome! Here's how you can help:

### 🐛 Reporting Issues
- Check [existing issues](https://github.com/ahoubouby/scala-multipart-client/issues)
- Provide detailed reproduction steps
- Include version information and environment details

### 💻 Contributing Code
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Write tests for your changes
4. Ensure all tests pass (`sbt test`)
5. Check code coverage (`sbt coverage test coverageReport`)
6. Format code (`sbt scalafmt`)
7. Commit your changes (`git commit -m 'Add amazing feature'`)
8. Push to the branch (`git push origin feature/amazing-feature`)
9. Open a Pull Request

### 📝 Contributing Documentation
- Fix typos and improve clarity
- Add examples and use cases
- Write tutorials and guides

### ✅ Code Quality Standards
- Minimum 70% test coverage for new code
- Follow existing code style (enforced by scalafmt)
- Write meaningful commit messages
- Add Scaladoc for public APIs

---

## License

MIT License - see [LICENSE](LICENSE) file for details

Copyright (c) 2025 Ahmed Houbouby

---

## Acknowledgments

Built with:
- [Apache Pekko](https://pekko.apache.org/) - Reactive streams
- [Play Framework](https://www.playframework.com/) - HTTP client
- [ScalaTest](https://www.scalatest.org/) - Testing framework

Inspired by the need for better multipart response handling in Scala applications.

---

## Support

- 📧 Email: ahoubouby@example.com
- 🐛 Issues: [GitHub Issues](https://github.com/ahoubouby/scala-multipart-client/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/ahoubouby/scala-multipart-client/discussions)

---

**⭐ Star this repo if you find it useful!**
