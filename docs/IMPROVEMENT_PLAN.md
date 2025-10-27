# Scala Multipart Client - Improvement Plan

**Version:** 1.0
**Date:** 2025-10-27
**Status:** Draft

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current State Assessment](#current-state-assessment)
3. [Missing Features & Gaps](#missing-features--gaps)
4. [Performance Optimization](#performance-optimization)
5. [Metrics & Benchmarking](#metrics--benchmarking)
6. [Logging & Observability](#logging--observability)
7. [Testing Strategy](#testing-strategy)
8. [Documentation Improvements](#documentation-improvements)
9. [Roadmap & Prioritization](#roadmap--prioritization)
10. [Success Criteria](#success-criteria)

---

## Executive Summary

This document provides a comprehensive improvement plan for the Scala Multipart Client library. The library has successfully resolved critical issues (415 errors, header handling) and now requires strategic enhancements to achieve production-ready quality.

### Key Focus Areas
- **Performance:** Stream processing optimizations, memory efficiency
- **Observability:** Structured logging, metrics, tracing
- **Testing:** Comprehensive test coverage, benchmarking suite
- **Features:** Missing HTTP capabilities, advanced error handling
- **Documentation:** User guides, performance tuning, migration guides

---

## Current State Assessment

### ✅ What's Working Well

#### 1. Core Functionality
- ✅ Multipart parsing with Boyer-Moore boundary detection
- ✅ Multiple format support (form-data, related, mixed)
- ✅ Fluent API builder pattern
- ✅ Play WS HTTP client integration
- ✅ JSON error response handling
- ✅ Content type classification

#### 2. Code Quality
- ✅ Clean architecture with clear layer separation
- ✅ Immutable data structures
- ✅ Type-safe API design
- ✅ Pluggable classifier system

#### 3. Recent Fixes
- ✅ Fixed 415 errors with proper charset handling
- ✅ Case-insensitive header operations
- ✅ URL query parameter encoding
- ✅ Content-Type precedence handling

### ⚠️ Areas Needing Attention

#### 1. Testing Coverage
- ⚠️ Limited unit tests (only 1 spec for MultipartRequestBuilder)
- ⚠️ No integration tests
- ⚠️ No performance benchmarks
- ⚠️ Missing edge case coverage (large files, streaming limits, etc.)

#### 2. Observability
- ⚠️ No structured logging
- ⚠️ No metrics collection
- ⚠️ No request/response tracing
- ⚠️ Debug logging was removed (good for prod, but need better alternative)

#### 3. Error Handling
- ⚠️ Limited error context in exceptions
- ⚠️ No retry mechanisms
- ⚠️ No circuit breaker patterns
- ⚠️ Timeout handling could be more granular

#### 4. Documentation
- ⚠️ No performance tuning guide
- ⚠️ No migration guide for version upgrades
- ⚠️ Limited real-world examples
- ⚠️ No comparison with alternatives

---

## Missing Features & Gaps

### 1. HTTP Client Features

#### Priority: HIGH
| Feature | Current State | Impact | Effort |
|---------|--------------|--------|--------|
| HTTP/2 Support | ❌ Missing | High | Medium |
| Retry with backoff | ❌ Missing | High | Low |
| Circuit breaker | ❌ Missing | High | Medium |
| Request interceptors | ❌ Missing | Medium | Low |
| Response interceptors | ❌ Missing | Medium | Low |
| Connection pooling config | ⚠️ Implicit (Play WS) | Medium | Low |
| Compression (gzip, deflate) | ⚠️ Implicit (Play WS) | Low | Low |

#### Implementation Notes
```scala
// Retry mechanism
.withRetry(
  maxAttempts = 3,
  backoff = ExponentialBackoff(initial = 1.second, max = 10.seconds)
)

// Circuit breaker
.withCircuitBreaker(
  failureThreshold = 5,
  resetTimeout = 30.seconds
)

// Interceptors
.withInterceptor { req =>
  // Log, modify headers, add tracing
  req.withHeader("X-Request-ID", UUID.randomUUID().toString)
}
```

### 2. Streaming & Large Files

#### Priority: HIGH
| Feature | Current State | Impact | Effort |
|---------|--------------|--------|--------|
| Streaming uploads | ❌ Missing | High | High |
| Streaming downloads | ⚠️ Partial (memory bounded) | High | Medium |
| Progress tracking | ❌ Missing | Medium | Medium |
| Chunk size configuration | ⚠️ Fixed | Medium | Low |
| Backpressure handling | ✅ Pekko Streams | N/A | N/A |

#### Implementation Notes
```scala
// Streaming upload
.withStreamingBody(
  source = FileIO.fromPath(Paths.get("large.pdf")),
  contentType = "application/pdf",
  contentLength = Some(fileSize)
)

// Progress tracking
.withProgressListener { progress =>
  println(s"Uploaded: ${progress.bytesTransferred}/${progress.totalBytes}")
}
```

### 3. Authentication & Security

#### Priority: MEDIUM
| Feature | Current State | Impact | Effort |
|---------|--------------|--------|--------|
| Bearer token | ✅ Implemented | N/A | N/A |
| Basic auth | ✅ Implemented | N/A | N/A |
| OAuth 2.0 | ❌ Missing | Medium | Medium |
| API key auth | ⚠️ Manual | Low | Low |
| Token refresh | ❌ Missing | Medium | Medium |
| mTLS support | ❌ Missing | Low | High |

#### Implementation Notes
```scala
// OAuth 2.0
.withOAuth2(
  tokenProvider = () => Future.successful(refreshToken()),
  autoRefresh = true
)

// API key
.withApiKey(key = "xxx", in = ApiKeyLocation.Header("X-API-Key"))
```

### 4. Response Processing

#### Priority: MEDIUM
| Feature | Current State | Impact | Effort |
|---------|--------------|--------|--------|
| Conditional parsing | ❌ Missing | Medium | Low |
| Lazy part access | ❌ Missing | Medium | Medium |
| Part streaming | ⚠️ All parts loaded | High | High |
| Selective part download | ❌ Missing | Medium | Medium |
| Response caching | ❌ Missing | Low | Medium |

#### Implementation Notes
```scala
// Conditional parsing
.executeIf(_.status == 200) // Only parse on success

// Lazy part access
result.parts.stream // Return Iterator instead of List

// Selective download
.withPartFilter(part => part.isPdf || part.isJson)
```

### 5. Configuration & Customization

#### Priority: LOW
| Feature | Current State | Impact | Effort |
|---------|--------------|--------|--------|
| Global defaults | ❌ Missing | Medium | Low |
| Custom serializers | ❌ Missing | Low | Medium |
| Request/response hooks | ❌ Missing | Medium | Low |
| Pluggable HTTP client | ⚠️ HttpClient trait exists | Medium | Medium |

---

## Performance Optimization

### 1. Current Performance Characteristics

#### Memory Usage
- **Headers:** Small, bounded by `maxHeaderSize` (4KB default)
- **Parts:** All loaded into memory (potential issue for large responses)
- **Streaming:** Good backpressure with Pekko Streams

#### CPU Usage
- **Boyer-Moore:** Efficient boundary search O(n/m)
- **Header parsing:** Linear scan for CRLFCRLF
- **Content-Type detection:** Efficient pattern matching

#### Bottlenecks Identified
1. **All parts buffered in memory** - No streaming access
2. **No connection pooling configuration** - Relies on Play WS defaults
3. **Header parsing** - Could use more efficient parsing
4. **Multiple string allocations** - In header processing

### 2. Optimization Opportunities

#### Priority: HIGH - Memory Efficiency

**Problem:** All parts are loaded into memory
```scala
// Current: All parts materialized
val parts: List[MultipartPart] = parseAllParts()

// Optimized: Lazy loading
val parts: LazyList[MultipartPart] = parsePartsLazily()
```

**Impact:**
- Current: 100MB response = 100MB+ memory
- Optimized: 100MB response = ~10MB memory (streaming)

**Implementation:**
```scala
// Add to MultipartResult
def partsStream: Source[MultipartPart, NotUsed]
def lazyParts: Iterator[MultipartPart]

// Allow filtering before materialization
def filterParts(predicate: PartInfo => Boolean): Future[List[MultipartPart]]
```

#### Priority: HIGH - Connection Pooling

**Problem:** No control over connection pooling
```scala
// Current: Play WS defaults
val wsClient = StandaloneAhcWSClient()
```

**Optimized:**
```scala
// Expose pooling configuration
val wsClient = StandaloneAhcWSClient(
  StandaloneAhcWSClientConfig(
    maxConnectionsPerHost = 100,
    maxConnectionsTotal = 200,
    connectionTimeout = 10.seconds,
    idleConnectionTimeout = 60.seconds,
    keepAlive = true
  )
)
```

**Impact:**
- Reduced connection establishment overhead
- Better resource utilization
- Configurable for different use cases

#### Priority: MEDIUM - Zero-Copy Operations

**Problem:** Multiple byte array copies
```scala
// Current: Multiple copies
val bytes = buffer.toArray  // Copy 1
val part = MultipartPart(bytes)  // Copy 2
```

**Optimized:**
```scala
// Use ByteString directly (no copying)
val bytes = buffer.compact  // No copy
val part = MultipartPart(bytes)  // Reference
```

#### Priority: MEDIUM - Header Parsing

**Problem:** Linear scan for CRLFCRLF
```scala
// Current: O(n) scan
val delimiterPos = buffer.indexOf(CRLFCRLF)
```

**Optimized:**
```scala
// Boyer-Moore for header delimiter too
val delimiterPos = BoyerMoore.search(buffer, CRLFCRLF)
```

**Impact:** Faster header parsing for large headers

#### Priority: LOW - String Interning

**Problem:** Common strings allocated repeatedly
```scala
// Current: New strings each time
val contentType = "application/json"
val charset = "UTF-8"
```

**Optimized:**
```scala
// Intern common values
object CommonValues {
  val ApplicationJson = "application/json"
  val CharsetUTF8 = "UTF-8"
}
```

### 3. Streaming Architecture

```
┌─────────────────────────────────────────────────┐
│           HTTP Response Stream                  │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
         ┌─────────────────────┐
         │  Boundary Detection │
         │  (Boyer-Moore)      │
         └─────────┬───────────┘
                   │
                   ▼
         ┌─────────────────────┐
         │  Header Extraction  │
         │  (Until CRLFCRLF)   │
         └─────────┬───────────┘
                   │
                   ▼
         ┌─────────────────────┐
         │  Part Classification│
         │  (Pluggable)        │
         └─────────┬───────────┘
                   │
                   ▼
┌────────────────────────────────────────────────┐
│  Body Extraction Strategy                      │
├────────────────────────────────────────────────┤
│  - Small parts (<1MB): Buffer in memory        │
│  - Large parts (>1MB): Stream to disk/callback │
│  - Filter unwanted parts: Skip body entirely   │
└────────────────────────────────────────────────┘
```

### 4. Performance Tuning Guide

#### Configuration Template
```scala
// Development: Fast feedback, debugging
val devConfig = MultipartParserConfig(
  boundary = "",
  maxMemoryBufferSize = 10 * 1024 * 1024,  // 10MB
  maxHeaderSize = 8192,  // 8KB
  enableDebugLogging = true
)

// Production: High throughput
val prodConfig = MultipartParserConfig(
  boundary = "",
  maxMemoryBufferSize = 100 * 1024 * 1024,  // 100MB
  maxHeaderSize = 4096,  // 4KB
  enableDebugLogging = false,
  streamingThreshold = Some(5 * 1024 * 1024)  // Stream parts >5MB
)

// Low memory: Streaming mode
val streamingConfig = MultipartParserConfig(
  boundary = "",
  maxMemoryBufferSize = 1 * 1024 * 1024,  // 1MB
  maxHeaderSize = 4096,
  streamingThreshold = Some(512 * 1024)  // Stream parts >512KB
)
```

---

## Metrics & Benchmarking

### 1. Key Performance Indicators (KPIs)

#### Request Metrics
| Metric | Description | Target | Critical |
|--------|-------------|--------|----------|
| Request latency (p50) | Median request time | <100ms | <500ms |
| Request latency (p95) | 95th percentile | <200ms | <1s |
| Request latency (p99) | 99th percentile | <500ms | <2s |
| Request throughput | Requests per second | >100 rps | >10 rps |
| Request success rate | % of successful requests | >99.9% | >99% |

#### Parsing Metrics
| Metric | Description | Target | Critical |
|--------|-------------|--------|----------|
| Parse time (p50) | Median parse time | <50ms | <200ms |
| Parse time (p95) | 95th percentile | <100ms | <500ms |
| Parse throughput | MB/s parsing rate | >50 MB/s | >10 MB/s |
| Memory overhead | Memory / response size | <2x | <5x |
| Part extraction time | Time per part | <10ms | <50ms |

#### Resource Metrics
| Metric | Description | Target | Critical |
|--------|-------------|--------|----------|
| Memory usage (peak) | Max memory allocated | <500MB | <2GB |
| Memory usage (avg) | Average memory | <100MB | <500MB |
| GC time (%) | % time in GC | <5% | <20% |
| Thread count | Active threads | <50 | <200 |
| Connection pool usage | Active connections | 80% | 95% |

#### Error Metrics
| Metric | Description | Target | Critical |
|--------|-------------|--------|----------|
| 4xx error rate | Client errors | <1% | <5% |
| 5xx error rate | Server errors | <0.1% | <1% |
| Timeout rate | Request timeouts | <0.1% | <1% |
| Parse error rate | Parsing failures | <0.01% | <0.1% |
| Retry rate | Requests retried | <5% | <20% |

### 2. Benchmarking Suite

#### Structure
```
benchmarks/
├── src/
│   └── main/
│       └── scala/
│           └── com/multipart/benchmarks/
│               ├── ParsingBenchmark.scala      # Multipart parsing speed
│               ├── RequestBenchmark.scala      # HTTP request overhead
│               ├── MemoryBenchmark.scala       # Memory allocation
│               ├── ConcurrencyBenchmark.scala  # Concurrent requests
│               └── ComparisonBenchmark.scala   # vs alternatives
└── README.md
```

#### Benchmark Scenarios

**1. Small Response (10KB, 3 parts)**
```scala
@Benchmark
def parseSmallResponse(): MultipartResult = {
  // Measure: Parse time, memory allocation
  // Expected: <10ms, <100KB memory
}
```

**2. Medium Response (1MB, 10 parts)**
```scala
@Benchmark
def parseMediumResponse(): MultipartResult = {
  // Measure: Parse time, throughput (MB/s)
  // Expected: <50ms, >50 MB/s
}
```

**3. Large Response (100MB, 50 parts)**
```scala
@Benchmark
def parseLargeResponse(): MultipartResult = {
  // Measure: Parse time, memory overhead
  // Expected: <2s, <3x memory
}
```

**4. Many Small Parts (1MB, 1000 parts)**
```scala
@Benchmark
def parseManyParts(): MultipartResult = {
  // Measure: Part extraction overhead
  // Expected: <100ms
}
```

**5. Concurrent Requests (100 concurrent)**
```scala
@Benchmark
def concurrentRequests(): List[MultipartResult] = {
  // Measure: Throughput, resource usage
  // Expected: >100 rps, stable memory
}
```

#### Comparison Matrix

Compare with alternative approaches:

| Library/Approach | Parse Time | Memory | Throughput | Features |
|------------------|------------|--------|------------|----------|
| **Scala Multipart Client** | Baseline | Baseline | Baseline | ⭐⭐⭐⭐⭐ |
| Play WS (built-in) | ? | ? | ? | ⭐⭐⭐ |
| Apache HttpClient | ? | ? | ? | ⭐⭐⭐⭐ |
| OkHttp | ? | ? | ? | ⭐⭐⭐⭐ |
| Manual parsing | ? | ? | ? | ⭐ |

#### Benchmark Execution

```bash
# Run all benchmarks
sbt "benchmarks/jmh:run -i 10 -wi 5 -f 1"

# Run specific benchmark
sbt "benchmarks/jmh:run ParsingBenchmark -i 10 -wi 5 -f 1"

# Generate flamegraphs
sbt "benchmarks/jmh:run -prof async:libPath=/path/to/libasyncProfiler.so"

# Memory profiling
sbt "benchmarks/jmh:run -prof gc"
```

### 3. Performance Monitoring

#### Instrumentation Points

```scala
// Request level
object Metrics {
  val requestLatency = Timer("multipart.request.latency")
  val requestErrors = Counter("multipart.request.errors")
  val requestsInFlight = Gauge("multipart.request.inflight")

  // Parsing level
  val parseLatency = Timer("multipart.parse.latency")
  val parseErrors = Counter("multipart.parse.errors")
  val partCount = Histogram("multipart.parts.count")
  val responseSize = Histogram("multipart.response.size")

  // Resource level
  val memoryUsage = Gauge("multipart.memory.bytes")
  val connectionPoolUsage = Gauge("multipart.connections.active")
}
```

#### Dashboard Template

```
┌─────────────────────────────────────────────────────────┐
│ Scala Multipart Client - Performance Dashboard         │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Request Latency (p50/p95/p99)                         │
│  ▓▓▓▓▓▓▓▓▓░░░░░ 45ms / 120ms / 280ms                   │
│                                                         │
│  Request Throughput                                     │
│  ▓▓▓▓▓▓▓▓▓▓▓▓▓ 125 rps                                 │
│                                                         │
│  Parse Latency (p50/p95/p99)                           │
│  ▓▓▓▓▓▓▓░░░░░░ 15ms / 45ms / 95ms                      │
│                                                         │
│  Memory Usage                                           │
│  ▓▓▓▓▓▓░░░░░░░ 125 MB / 500 MB                         │
│                                                         │
│  Error Rate (4xx/5xx/timeout)                          │
│  ▓░░░░░░░░░░░░ 0.5% / 0.1% / 0.05%                     │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 4. Regression Testing

#### Automated Performance Tests

```scala
class PerformanceRegressionSpec extends AnyWordSpec {

  "MultipartParser" should {

    "parse 1MB response in under 100ms" in {
      val start = System.nanoTime()
      val result = parseResponse(createResponse(1.MB, 10))
      val duration = (System.nanoTime() - start).nanos

      duration should be < 100.millis
    }

    "use less than 3x memory overhead" in {
      val responseSize = 10.MB
      val memBefore = Runtime.getRuntime.totalMemory - Runtime.getRuntime.freeMemory

      val result = parseResponse(createResponse(responseSize, 50))

      val memAfter = Runtime.getRuntime.totalMemory - Runtime.getRuntime.freeMemory
      val memUsed = memAfter - memBefore

      memUsed should be < (responseSize * 3)
    }

    "maintain throughput under concurrent load" in {
      val requests = (1 to 100).map(_ => Future {
        parseResponse(createResponse(1.MB, 10))
      })

      val start = System.nanoTime()
      Await.result(Future.sequence(requests), 10.seconds)
      val duration = (System.nanoTime() - start).nanos

      val throughput = 100.0 / duration.toSeconds
      throughput should be > 50.0 // >50 rps
    }
  }
}
```

---

## Logging & Observability

### 1. Current State

**Problems:**
- No structured logging
- Debug logging was removed
- No tracing correlation
- No metrics collection
- Hard to troubleshoot production issues

### 2. Structured Logging

#### Log Levels

```scala
// ERROR: Unrecoverable errors
logger.error("Failed to parse multipart response",
  "url" -> request.url,
  "status" -> response.status,
  "contentType" -> response.contentType,
  "error" -> exception.getMessage
)

// WARN: Recoverable issues
logger.warn("Large response detected, may impact performance",
  "url" -> request.url,
  "size" -> response.contentLength,
  "parts" -> partCount
)

// INFO: Important events
logger.info("Multipart request completed",
  "url" -> request.url,
  "status" -> response.status,
  "parts" -> result.parts.size,
  "duration" -> duration.toMillis
)

// DEBUG: Detailed information
logger.debug("Parsing part",
  "partIndex" -> index,
  "contentType" -> part.contentType,
  "size" -> part.size
)

// TRACE: Very detailed (disabled in production)
logger.trace("Boundary detected",
  "position" -> position,
  "boundary" -> boundary
)
```

#### Log Format (JSON)

```json
{
  "timestamp": "2025-10-27T14:30:45.123Z",
  "level": "INFO",
  "logger": "com.multipart.api.MultipartRequestBuilder",
  "message": "Multipart request completed",
  "requestId": "req-123-456-789",
  "traceId": "trace-abc-def-ghi",
  "context": {
    "url": "https://api.example.com/upload",
    "method": "POST",
    "status": 200,
    "parts": 5,
    "duration": 245
  },
  "thread": "pekko-actor-default-1"
}
```

### 3. Tracing & Context Propagation

#### Request ID Propagation

```scala
// Generate or extract request ID
val requestId = request.headers
  .get("X-Request-ID")
  .getOrElse(UUID.randomUUID().toString)

// Propagate through logs
implicit val context: RequestContext = RequestContext(
  requestId = requestId,
  traceId = extractTraceId(request),
  spanId = UUID.randomUUID().toString
)

// Add to all log statements
logger.info("Starting request")(context)
```

#### Distributed Tracing Integration

```scala
// OpenTelemetry integration
import io.opentelemetry.api.trace._

def execute()(implicit tracer: Tracer): Future[MultipartResult] = {
  val span = tracer.spanBuilder("multipart.request")
    .setAttribute("http.url", url)
    .setAttribute("http.method", method.name)
    .startSpan()

  try {
    httpClient.execute(request).flatMap { response =>
      span.setAttribute("http.status", response.status)
      MultipartParser.parse(response, parserConfig)
    }
  } finally {
    span.end()
  }
}
```

### 4. Observability Features

#### Request/Response Logging

```scala
// Add to PlayWSHttpClient
class PlayWSHttpClient(
  wsClient: StandaloneWSClient,
  baseUrl: String = "",
  logger: Option[Logger] = None,
  logLevel: LogLevel = LogLevel.INFO
) {

  override def execute(request: HttpRequest): Future[HttpResponse] = {
    val start = System.nanoTime()

    logger.foreach(_.debug(
      "Sending request",
      "url" -> request.url,
      "method" -> request.method.name,
      "headers" -> request.headers.keys.mkString(", ")
    ))

    buildRequest(request)
      .stream()
      .map(new PlayWSHttpResponse(_))
      .andThen {
        case Success(response) =>
          val duration = (System.nanoTime() - start).nanos
          logger.foreach(_.info(
            "Request completed",
            "url" -> request.url,
            "status" -> response.status,
            "duration" -> duration.toMillis
          ))

        case Failure(exception) =>
          val duration = (System.nanoTime() - start).nanos
          logger.foreach(_.error(
            "Request failed",
            "url" -> request.url,
            "duration" -> duration.toMillis,
            "error" -> exception.getMessage
          ))
      }
  }
}
```

#### Metrics Collection

```scala
// Add metrics to key operations
class MetricsCollector {
  private val requestTimer = Timer("multipart.request.duration")
  private val parseTimer = Timer("multipart.parse.duration")
  private val partCounter = Counter("multipart.parts.count")
  private val errorCounter = Counter("multipart.errors")

  def recordRequest[T](url: String)(f: => Future[T]): Future[T] = {
    val start = System.nanoTime()
    f.andThen {
      case Success(_) =>
        requestTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
      case Failure(ex) =>
        errorCounter.increment(Map("error" -> ex.getClass.getSimpleName))
    }
  }
}
```

### 5. Debug Mode

```scala
// Enable debug mode for troubleshooting
val client = Multipart
  .request(httpClient)
  .withDebugMode(enabled = true)
  .post("/api/upload")
  .execute()

// Debug output:
// [DEBUG] Request URL: POST https://api.example.com/api/upload
// [DEBUG] Request headers:
//   - Content-Type: application/json
//   - Authorization: Bearer xxx...
// [DEBUG] Request body size: 1024 bytes
// [DEBUG] Response status: 200 OK
// [DEBUG] Response headers:
//   - Content-Type: multipart/mixed; boundary=----boundary
// [DEBUG] Response size: 5242880 bytes
// [DEBUG] Parsing started
// [DEBUG] Boundary detected at position 47
// [DEBUG] Part 1: application/json, 512 bytes
// [DEBUG] Part 2: application/pdf, 5242368 bytes
// [DEBUG] Parsing completed in 245ms
```

### 6. Health Checks

```scala
// Add health check endpoint
object HealthCheck {
  def check(): Future[HealthStatus] = {
    Future {
      HealthStatus(
        status = "healthy",
        version = BuildInfo.version,
        checks = Map(
          "http_client" -> checkHttpClient(),
          "memory" -> checkMemory(),
          "threads" -> checkThreads()
        )
      )
    }
  }

  private def checkHttpClient(): CheckResult = {
    // Verify HTTP client can make requests
    CheckResult(
      status = "healthy",
      message = "HTTP client operational"
    )
  }

  private def checkMemory(): CheckResult = {
    val runtime = Runtime.getRuntime
    val usedMemory = runtime.totalMemory - runtime.freeMemory
    val maxMemory = runtime.maxMemory
    val usagePercent = (usedMemory.toDouble / maxMemory) * 100

    CheckResult(
      status = if (usagePercent < 90) "healthy" else "warning",
      message = f"Memory usage: $usagePercent%.1f%%",
      details = Map(
        "used" -> usedMemory,
        "max" -> maxMemory
      )
    )
  }
}
```

---

## Testing Strategy

### 1. Test Coverage Goals

| Component | Current Coverage | Target Coverage |
|-----------|-----------------|-----------------|
| API layer | ~20% | >90% |
| HTTP client | 0% | >80% |
| Parser | ~60% | >95% |
| Classifier | ~70% | >90% |
| Model | ~50% | >85% |
| Utils | ~80% | >90% |
| **Overall** | **~40%** | **>85%** |

### 2. Test Categories

#### Unit Tests

```
src/test/scala/com/multipart/
├── api/
│   ├── MultipartRequestBuilderSpec.scala      ✅ EXISTS (expand)
│   └── MultipartSpec.scala                    ❌ TODO
├── client/
│   ├── PlayWSHttpClientSpec.scala             ❌ TODO (critical)
│   ├── HttpRequestSpec.scala                  ❌ TODO
│   └── HttpResponseSpec.scala                 ❌ TODO
├── parser/
│   ├── MultipartParserSpec.scala              ✅ EXISTS (expand)
│   ├── FormatDetectorSpec.scala               ✅ EXISTS
│   ├── JsonErrorResponseSpec.scala            ✅ EXISTS
│   └── IncompleteMultipartSpec.scala          ✅ EXISTS
├── classifier/
│   └── ClassifierSpec.scala                   ✅ EXISTS
├── model/
│   ├── MultipartResultSpec.scala              ✅ EXISTS
│   └── MultipartPartSpec.scala                ✅ EXISTS
└── utils/
    ├── BoyerMooreSpec.scala                   ✅ EXISTS
    └── ContentTypeDetectorSpec.scala          ✅ EXISTS
```

#### Integration Tests

```
src/it/scala/com/multipart/
├── RealApiIntegrationSpec.scala              ❌ TODO (test with real APIs)
├── ErrorHandlingIntegrationSpec.scala        ❌ TODO (network errors, timeouts)
├── LargeFileIntegrationSpec.scala            ❌ TODO (stress test)
└── ConcurrencyIntegrationSpec.scala          ❌ TODO (concurrent requests)
```

#### Property-Based Tests

```scala
// Use ScalaCheck for property testing
class MultipartParserPropertySpec extends AnyPropSpec with PropertyChecks {

  property("parsing should be idempotent") {
    forAll(multipartResponseGen) { response =>
      val result1 = parse(response)
      val result2 = parse(response)
      result1 shouldEqual result2
    }
  }

  property("part count should match boundary count - 1") {
    forAll(multipartResponseGen) { response =>
      val boundaryCount = countBoundaries(response)
      val partCount = parse(response).parts.size
      partCount shouldEqual (boundaryCount - 1)
    }
  }
}
```

### 3. Test Scenarios

#### Edge Cases to Cover

1. **Empty responses**
   - 0 parts
   - Only boundary markers
   - No content

2. **Malformed responses**
   - Missing boundary
   - Invalid headers
   - Truncated parts
   - Missing CRLFCRLF
   - Invalid Content-Type

3. **Large responses**
   - 1000+ parts
   - 100MB+ files
   - Very long headers

4. **Special characters**
   - Unicode in headers
   - Binary data in headers
   - Boundary-like sequences in data

5. **Concurrent access**
   - Multiple simultaneous requests
   - Shared resources
   - Thread safety

6. **Network failures**
   - Connection timeout
   - Read timeout
   - Connection reset
   - Slow responses

### 4. Test Data Management

#### Fixtures

```scala
object TestFixtures {
  // Small test data
  def smallResponse: Array[Byte] = ???

  // Real-world samples
  def colissimoResponse: Array[Byte] = loadResource("samples/colissimo.bin")
  def amazonS3Response: Array[Byte] = loadResource("samples/s3.bin")

  // Edge cases
  def emptyResponse: Array[Byte] = ???
  def malformedResponse: Array[Byte] = ???

  // Generators
  def randomMultipartResponse(
    parts: Int = 5,
    avgPartSize: Int = 1024
  ): Array[Byte] = ???
}
```

---

## Documentation Improvements

### 1. Missing Documentation

#### User Guides

- [ ] **Getting Started Guide** - 5-minute quickstart
- [ ] **Migration Guide** - Upgrading between versions
- [ ] **Best Practices** - Common patterns and anti-patterns
- [ ] **Performance Tuning** - Optimization guide
- [ ] **Error Handling Guide** - Handling different error scenarios
- [ ] **Security Guide** - Authentication, HTTPS, etc.

#### API Documentation

- [ ] **Complete Scaladoc** - All public APIs
- [ ] **Code Examples** - For all major features
- [ ] **FAQ** - Common questions
- [ ] **Cookbook** - Recipe-style solutions

#### Reference Documentation

- [ ] **Configuration Reference** - All config options
- [ ] **Metrics Reference** - All available metrics
- [ ] **Error Reference** - All error codes/exceptions
- [ ] **Comparison Guide** - vs other libraries

### 2. Documentation Structure

```
docs/
├── README.md                      ✅ Main entry point
├── ARCHITECTURE.md                ✅ System design
├── TROUBLESHOOTING.md             ✅ Common issues
├── PARSING_ISSUES_ANALYSIS.md     ✅ Parsing details
├── JSON_ERROR_HANDLING.md         ✅ Error handling
├── PUBLISHING.md                  ✅ Release process
├── IMPROVEMENT_PLAN.md            📝 This document
│
├── guides/
│   ├── getting-started.md         ❌ TODO
│   ├── migration-guide.md         ❌ TODO
│   ├── best-practices.md          ❌ TODO
│   ├── performance-tuning.md      ❌ TODO
│   ├── error-handling.md          ❌ TODO
│   └── security.md                ❌ TODO
│
├── api/
│   ├── multipart-builder.md       ❌ TODO
│   ├── http-client.md             ❌ TODO
│   ├── parser.md                  ❌ TODO
│   └── classifiers.md             ❌ TODO
│
├── reference/
│   ├── configuration.md           ❌ TODO
│   ├── metrics.md                 ❌ TODO
│   ├── errors.md                  ❌ TODO
│   └── comparison.md              ❌ TODO
│
└── examples/
    ├── basic-usage.md             ❌ TODO
    ├── authentication.md          ❌ TODO
    ├── large-files.md             ❌ TODO
    ├── error-recovery.md          ❌ TODO
    └── advanced.md                ❌ TODO
```

### 3. Example Gallery

Expand examples to cover:
- ✅ Shipping labels (Colissimo API) - EXISTS
- ❌ File uploads to S3
- ❌ REST API with JSON + files
- ❌ SOAP API with attachments
- ❌ Email with attachments
- ❌ Streaming large files
- ❌ Retry and error handling
- ❌ Custom authentication

---

## Roadmap & Prioritization

### Phase 1: Stability & Testing (v0.2.0) - 2-3 weeks

**Goal:** Production-ready quality

#### High Priority
- [ ] Add comprehensive unit tests (target >80% coverage)
- [ ] Add PlayWSHttpClient tests
- [ ] Add integration tests with test servers
- [ ] Fix any discovered bugs
- [ ] Add retry mechanism
- [ ] Improve error messages and context

#### Medium Priority
- [ ] Add basic structured logging
- [ ] Add request/response interceptors
- [ ] Document all public APIs
- [ ] Create getting started guide

#### Deliverables
- ✅ Test coverage >80%
- ✅ Zero known critical bugs
- ✅ Basic documentation complete

### Phase 2: Performance & Observability (v0.3.0) - 3-4 weeks

**Goal:** Production monitoring and optimization

#### High Priority
- [ ] Create benchmarking suite
- [ ] Baseline performance metrics
- [ ] Add metrics collection
- [ ] Add distributed tracing support
- [ ] Implement streaming for large files
- [ ] Add connection pooling configuration

#### Medium Priority
- [ ] Optimize memory usage (lazy loading)
- [ ] Add performance regression tests
- [ ] Create performance dashboard
- [ ] Performance tuning guide

#### Deliverables
- ✅ Benchmarking suite operational
- ✅ Metrics and tracing integrated
- ✅ Performance optimized (meets targets)
- ✅ Performance guide published

### Phase 3: Features & Enhancements (v0.4.0) - 4-6 weeks

**Goal:** Feature completeness

#### High Priority
- [ ] HTTP/2 support
- [ ] Circuit breaker pattern
- [ ] Streaming uploads/downloads
- [ ] Progress tracking
- [ ] OAuth 2.0 support

#### Medium Priority
- [ ] Response caching
- [ ] Conditional parsing
- [ ] Custom serializers
- [ ] Alternative HTTP clients (OkHttp, etc.)

#### Deliverables
- ✅ All high-priority features implemented
- ✅ Comprehensive example gallery
- ✅ Complete API documentation

### Phase 4: Ecosystem & Polish (v1.0.0) - 2-3 weeks

**Goal:** Version 1.0 release

#### High Priority
- [ ] Security audit
- [ ] Performance audit
- [ ] Documentation review
- [ ] Migration guide from 0.x
- [ ] Release notes

#### Medium Priority
- [ ] Comparison guide vs alternatives
- [ ] Video tutorials
- [ ] Blog posts / articles
- [ ] Community feedback incorporation

#### Deliverables
- ✅ Version 1.0.0 released
- ✅ Production-ready certification
- ✅ Complete documentation
- ✅ Community engagement

### Timeline Summary

```
Month 1       Month 2       Month 3       Month 4
├─────────────┼─────────────┼─────────────┼─────────────┐
│   Phase 1   │   Phase 2   │   Phase 3   │   Phase 4   │
│  Stability  │ Performance │  Features   │    v1.0     │
└─────────────┴─────────────┴─────────────┴─────────────┘
     v0.2.0       v0.3.0       v0.4.0        v1.0.0
```

---

## Success Criteria

### Functional Requirements

- ✅ **Multipart parsing:** Parse all multipart formats (form-data, related, mixed)
- ✅ **Error handling:** Graceful handling of malformed responses
- ✅ **Streaming:** Support for large files without memory issues
- ✅ **Performance:** Parse at >50 MB/s, <100ms latency for typical responses
- ✅ **Testing:** >85% code coverage, integration tests passing

### Non-Functional Requirements

- ✅ **Reliability:** >99.9% success rate in production
- ✅ **Performance:** Meet or exceed targets in all benchmarks
- ✅ **Observability:** Full metrics, logging, and tracing
- ✅ **Documentation:** Complete, accurate, and helpful
- ✅ **Maintainability:** Clean code, comprehensive tests

### User Satisfaction

- ✅ **Ease of use:** 5-minute setup for basic use case
- ✅ **Documentation:** Users can solve problems without support
- ✅ **Performance:** Meets or exceeds user expectations
- ✅ **Reliability:** No production incidents
- ✅ **Community:** Active users, contributions, and feedback

### Business Metrics

- ✅ **Adoption:** Used in production by multiple teams
- ✅ **Stability:** <1 critical bug per month
- ✅ **Performance:** Zero performance regressions
- ✅ **Support:** <2 hour response time for critical issues

---

## Appendix A: Quick Wins

Immediate improvements with high impact and low effort:

1. **Add retry mechanism** (2-3 hours)
   - Exponential backoff
   - Configurable attempts
   - Idempotency awareness

2. **Improve error messages** (1-2 hours)
   - Include more context
   - Suggest solutions
   - Add error codes

3. **Add request/response logging** (2-3 hours)
   - Optional debug mode
   - Structured JSON logs
   - Sanitize sensitive data

4. **Create getting started guide** (3-4 hours)
   - 5-minute quickstart
   - Common use cases
   - Troubleshooting tips

5. **Add more unit tests** (4-8 hours)
   - PlayWSHttpClient
   - MultipartRequestBuilder
   - Edge cases

---

## Appendix B: Technical Debt

Known issues to address:

1. **All parts loaded in memory**
   - Risk: OOM for large responses
   - Solution: Streaming access to parts

2. **No connection pooling control**
   - Risk: Resource exhaustion
   - Solution: Expose pooling config

3. **Limited HTTP client abstraction**
   - Risk: Tight coupling to Play WS
   - Solution: Better abstraction, more implementations

4. **No timeout granularity**
   - Risk: Can't control connect vs read timeouts
   - Solution: Separate timeout configs

5. **Debug logging removed**
   - Risk: Hard to troubleshoot production issues
   - Solution: Structured logging with levels

---

## Appendix C: Resources

### Tools
- **JMH:** Java Microbenchmark Harness for benchmarking
- **async-profiler:** CPU and allocation profiling
- **VisualVM:** Memory profiling and analysis
- **Gatling:** Load testing
- **ScalaCheck:** Property-based testing

### Libraries
- **Micrometer:** Metrics collection
- **OpenTelemetry:** Distributed tracing
- **Logback:** Structured logging
- **ScalaTest:** Testing framework

### References
- **RFC 2046:** Multipart Media Types
- **RFC 7578:** multipart/form-data
- **RFC 2387:** multipart/related
- **Play WS Docs:** HTTP client documentation
- **Pekko Streams:** Reactive streams documentation

---

**Document Status:** Draft
**Next Review:** 2025-11-03
**Owner:** Development Team
**Contributors:** [List contributors]
