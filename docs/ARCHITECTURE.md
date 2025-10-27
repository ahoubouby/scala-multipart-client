# Architecture Overview

## Layer Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         APPLICATION                                 │
│                  (Your Scala/Play Application)                      │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      FLUENT API LAYER                               │
│                     (api/Multipart.scala)                           │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Multipart.request(httpClient)                               │   │
│  │    .post("/api/endpoint")                                    │   │
│  │    .withAuth(token)                                          │   │
│  │    .withJsonBody(payload)                                    │   │
│  │    .execute()                                                │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     HTTP CLIENT LAYER                               │
│                 (client/HttpClient.scala)                           │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  • Abstract HttpClient trait                                 │   │
│  │  • PlayWSHttpClient implementation                           │   │
│  │  • HttpRequest / HttpResponse abstractions                   │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   FORMAT DETECTION LAYER                            │
│              (parser/FormatDetector.scala)                          │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  • Extract boundary from Content-Type                        │   │
│  │  • Detect format: form-data / related / mixed                │   │
│  │  • Extract start parameter (for multipart/related)           │   │
│  │  • Select appropriate classifiers                            │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    STREAM PROCESSING LAYER                          │
│          (parser/BodyPartParser.scala + Pekko Streams)              │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  1. Boyer-Moore boundary detection                           │   │
│  │  2. Header parsing (until CRLFCRLF)                          │   │
│  │  3. Part classification (pluggable)                          │   │
│  │  4. Body extraction                                          │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  CLASSIFICATION LAYER                               │
│              (classifier/PartClassifier.scala)                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  • FormDataClassifier (Content-Disposition)                  │   │
│  │  • RelatedClassifier (Content-ID)                            │   │
│  │  • MixedClassifier (Content-ID / Content-Location)           │   │
│  │  • UnknownClassifier (fallback)                              │   │
│  │  • ChainedClassifier (chain of responsibility)               │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     RESULT ASSEMBLY LAYER                           │
│                  (model/MultipartResult.scala)                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  • MultipartPart (info + data)                               │   │
│  │  • MultipartResult (parts + metadata)                        │   │
│  │  • Content type detection                                    │   │
│  │  • Pattern-based access                                      │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

## Data Flow

```
HTTP Response
      │
      ▼
┌──────────────┐
│ ByteString   │  Streaming bytes
│   Stream     │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Boundary    │  Split on boundaries using Boyer-Moore
│  Detection   │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Header     │  Parse headers (until CRLFCRLF)
│   Parsing    │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│    Part      │  Classify using pluggable classifiers
│Classification│
└──────┬───────┘
       │
       ▼
┌──────────────┐
│    Body      │  Extract body bytes
│  Extraction  │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ MultipartPart│  Assembled parts
└──────┬───────┘
       │
       ▼
┌──────────────┐
│MultipartResult│  Final result
└──────────────┘
```

## Package Responsibilities

### `com.multipart.model`
- **Purpose**: Core data structures
- **Key Classes**:
    - `PartInfo` - Information about a part
    - `MultipartPart` - A single part (info + data)
    - `MultipartResult` - Complete parsed result
    - `MultipartFormat` - Format enumeration

### `com.multipart.client`
- **Purpose**: HTTP client abstraction
- **Key Classes**:
    - `HttpClient` - Abstract client interface
    - `PlayWSHttpClient` - Play WS implementation
    - `HttpRequest` / `HttpResponse` - Generic types

### `com.multipart.classifier`
- **Purpose**: Part classification strategies
- **Key Classes**:
    - `PartClassifier` - Strategy trait
    - `FormDataClassifier` - For multipart/form-data
    - `RelatedClassifier` - For multipart/related
    - `ChainedClassifier` - Chain of responsibility

### `com.multipart.parser`
- **Purpose**: Stream-based parsing
- **Key Classes**:
    - `MultipartParser` - High-level parser
    - `BodyPartParser` - Pekko Streams parser
    - `MultipartParserConfig` - Configuration
    - `FormatDetector` - Format detection

### `com.multipart.utils`
- **Purpose**: Utility functions
- **Key Classes**:
    - `ContentTypeDetector` - Detect content types
    - `BoyerMoore` - String search algorithm

### `com.multipart.api`
- **Purpose**: Fluent API
- **Key Classes**:
    - `Multipart` - Entry point
    - `MultipartRequestBuilder` - Fluent builder

## Extension Points

### 1. Custom Classifiers
```scala
object MyClassifier extends PartClassifier {
  def classify(headers: Map[String, String]): Option[PartInfo] = {
    // Your logic here
  }
}
```

### 2. Custom HTTP Clients
```scala
class MyHttpClient extends HttpClient {
  def execute(request: HttpRequest): Future[HttpResponse] = {
    // Your implementation
  }
}
```

### 3. Custom Part Info
```scala
case class CustomPartInfo(
  id: String,
  customField: String,
  contentType: Option[String]
) extends PartInfo {
  def identifier: String = id
  def metadata: Map[String, String] = Map(
    "id" -> id,
    "custom" -> customField
  )
}
```

## Performance Considerations

### Memory Efficiency
- ✅ Streaming-based parsing (no need to buffer entire response)
- ✅ Boyer-Moore algorithm for efficient boundary detection
- ✅ Configurable buffer sizes

### CPU Efficiency
- ✅ O(n/m) average case for Boyer-Moore
- ✅ Single-pass parsing
- ✅ Lazy evaluation where possible

### Scalability
- ✅ Backpressure handling via Pekko Streams
- ✅ Non-blocking async operations
- ✅ Supervision strategies for error recovery

## Testing Strategy

### Unit Tests
- Model classes (PartInfo, MultipartPart, etc.)
- Classifiers (each classifier in isolation)
- Utilities (ContentTypeDetector, etc.)

### Integration Tests
- HTTP client implementations
- Parser with different formats
- End-to-end scenarios

### Property-Based Tests
- Boundary detection edge cases
- Header parsing variations
- Format detection combinations
  EOF
  cat ARCHITECTURE.md
  Output
│                  CLASSIFICATION LAYER                               │
│              (classifier/PartClassifier.scala)                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  • FormDataClassifier (Content-Disposition)                  │  │
│  │  • RelatedClassifier (Content-ID)                            │  │
│  │  • MixedClassifier (Content-ID / Content-Location)           │  │
│  │  • UnknownClassifier (fallback)                              │  │
│  │  • ChainedClassifier (chain of responsibility)               │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     RESULT ASSEMBLY LAYER                           │
│                  (model/MultipartResult.scala)                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  • MultipartPart (info + data)                               │  │
│  │  • MultipartResult (parts + metadata)                        │  │
│  │  • Content type detection                                    │  │
│  │  • Pattern-based access                                      │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

## Data Flow

```
HTTP Response
      │
      ▼
┌──────────────┐
│ ByteString   │  Streaming bytes
│   Stream     │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Boundary    │  Split on boundaries using Boyer-Moore
│  Detection   │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Header     │  Parse headers (until CRLFCRLF)
│   Parsing    │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│    Part      │  Classify using pluggable classifiers
│Classification│
└──────┬───────┘
       │
       ▼
┌──────────────┐
│    Body      │  Extract body bytes
│  Extraction  │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ MultipartPart│  Assembled parts
└──────┬───────┘
       │
       ▼
┌──────────────┐
│MultipartResult│  Final result
└──────────────┘
```

## Package Responsibilities

### `com.multipart.model`
- **Purpose**: Core data structures
- **Key Classes**:
- `PartInfo` - Information about a part
- `MultipartPart` - A single part (info + data)
- `MultipartResult` - Complete parsed result
- `MultipartFormat` - Format enumeration

### `com.multipart.client`
- **Purpose**: HTTP client abstraction
- **Key Classes**:
- `HttpClient` - Abstract client interface
- `PlayWSHttpClient` - Play WS implementation
- `HttpRequest` / `HttpResponse` - Generic types

### `com.multipart.classifier`
- **Purpose**: Part classification strategies
- **Key Classes**:
- `PartClassifier` - Strategy trait
- `FormDataClassifier` - For multipart/form-data
- `RelatedClassifier` - For multipart/related
- `ChainedClassifier` - Chain of responsibility

### `com.multipart.parser`
- **Purpose**: Stream-based parsing
- **Key Classes**:
- `MultipartParser` - High-level parser
- `BodyPartParser` - Pekko Streams parser
- `MultipartParserConfig` - Configuration
- `FormatDetector` - Format detection

### `com.multipart.utils`
- **Purpose**: Utility functions
- **Key Classes**:
- `ContentTypeDetector` - Detect content types
- `BoyerMoore` - String search algorithm

### `com.multipart.api`
- **Purpose**: Fluent API
- **Key Classes**:
- `Multipart` - Entry point
- `MultipartRequestBuilder` - Fluent builder

## Extension Points

### 1. Custom Classifiers
```scala
object MyClassifier extends PartClassifier {
def classify(headers: Map[String, String]): Option[PartInfo] = {
// Your logic here
}
}
```

### 2. Custom HTTP Clients
```scala
class MyHttpClient extends HttpClient {
def execute(request: HttpRequest): Future[HttpResponse] = {
// Your implementation
}
}
```

### 3. Custom Part Info
```scala
case class CustomPartInfo(
id: String,
customField: String,
contentType: Option[String]
) extends PartInfo {
def identifier: String = id
def metadata: Map[String, String] = Map(
"id" -> id,
"custom" -> customField
)
}
```

## Performance Considerations

### Memory Efficiency
- ✅ Streaming-based parsing (no need to buffer entire response)
- ✅ Boyer-Moore algorithm for efficient boundary detection
- ✅ Configurable buffer sizes

### CPU Efficiency
- ✅ O(n/m) average case for Boyer-Moore
- ✅ Single-pass parsing
- ✅ Lazy evaluation where possible

### Scalability
- ✅ Backpressure handling via Pekko Streams
- ✅ Non-blocking async operations
- ✅ Supervision strategies for error recovery

## Testing Strategy

### Unit Tests
- Model classes (PartInfo, MultipartPart, etc.)
- Classifiers (each classifier in isolation)
- Utilities (ContentTypeDetector, etc.)

### Integration Tests
- HTTP client implementations
- Parser with different formats
- End-to-end scenarios

### Property-Based Tests
- Boundary detection edge cases
- Header parsing variations
- Format detection combinations
