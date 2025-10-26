# Multipart Parsing Issues - Comprehensive Analysis

## Issue #1: Leading CRLF Boundary Detection

### The Problem

**RFC 2046 Standard:**
- First boundary: `--boundary` (no leading CRLF)
- Subsequent boundaries: `\r\n--boundary` (with leading CRLF)

**Your Server Sends:**
```
\r\n--uuid:xxx...\r\n
Content-Type: ...
```
The FIRST boundary has a leading `\r\n`, which is **technically non-standard** but common in practice.

### How The Parser Handles It

**Current Implementation:**
```scala
// Boundary pattern includes CRLF prefix
private val boundaryBytes = Array('\r', '\n', '-', '-') ++ boundary.getBytes()
```

**Detection Flow:**
1. **InitialBoundary Phase**: Tries to match `--boundary` at position 0 (without CRLF)
   - Looks for `--` at position 0
   - But finds `\r\n` instead
   - **Match fails** ❌

2. **Falls to Preamble Phase**: Uses Boyer-Moore to find `\r\n--boundary` anywhere
   - Searches for full pattern `\r\n--uuid:...`
   - **Finds it at position 0** ✅
   - Transitions to `Headers(47, 0)`

3. **Now Headers Phase Runs**: Thanks to our fix!
   - Searches for `\r\n\r\n` from position 47
   - Finds it at position 151
   - **Parses headers successfully** ✅

### Is It Generic Enough?

**YES!** ✅ This solution handles all cases:

| Server Behavior | Parser Handles? | How? |
|----------------|----------------|------|
| Standard: `--boundary` at start | ✅ YES | InitialBoundary matches |
| Non-standard: `\r\n--boundary` at start | ✅ YES | Preamble finds it at position 0 |
| With preamble text: `text\r\n--boundary` | ✅ YES | Preamble skips text and finds boundary |
| Multiple parts | ✅ YES | Uses boundary pattern `\r\n--boundary` for all subsequent |

### Potential Improvements

If you want to be more explicit, you could add a check in InitialBoundary:

```scala
case InitialBoundary =>
  // Try standard format first: --boundary
  if (matchesBoundary(s.input, 0, s.boundary)) {
    // Standard format
  }
  // Try non-standard format: \r\n--boundary
  else if (s.input.length >= 2 && crlf(s.input, 0) && matchesBoundary(s.input, 2, s.boundary)) {
    // Non-standard with leading CRLF
    val ix = s.boundaryLen + 2  // Skip the leading CRLF
    if (crlf(s.input, ix)) Step(s.copy(phase = Headers(ix + 2, 0)), Nil)
    ...
  }
  else {
    // Fall to Preamble
    Step(s.copy(phase = Preamble(0)), Nil)
  }
```

But the current solution via Preamble is actually cleaner and more robust!

---

## Issue #2: Dynamic Buffer Size for Large Files

### The Problem

**Current Approach:**
```scala
// Manual configuration required
val parserConfig = MultipartParserConfig(
  maxMemoryBufferSize = 1024 * 1024,  // Must specify 1MB manually
)
```

**Why This Happens:**
- PDF with `Content-ID: <label>` is classified as `RelatedPartInfo`
- `RelatedPartInfo` parts are buffered in memory (for JSON/XML data)
- But your PDF is 82KB, exceeding the 16KB default

### Root Cause: Classification Logic

The problem is in how parts are classified:

```scala
// In Parser.scala Headers phase:
val info = classify(headers, s.classifiers)

info match {
  case f: FormDataPartInfo if f.filename.isDefined =>
    // Has filename -> FileData phase (STREAMING) ✅
    Step(FileData(...), EmitPart(FilePart(...)))

  case f: FormDataPartInfo =>
    // No filename -> DataBody phase (BUFFERED) for form fields

  case r: RelatedPartInfo =>
    // multipart/related -> DataBody phase (BUFFERED) ❌
    // Problem: PDFs in related parts are buffered!

  case u: UnknownPartInfo =>
    // Unknown -> BadBody phase (BUFFERED)
}
```

### Better Solutions

#### Option A: Stream All Large Content-Types (RECOMMENDED)

Modify the Headers phase to check `Content-Type` and size hints:

```scala
case Headers(start, mem) =>
  val idx = s.input.indexOfSlice(s.crlfcrlf, start)
  if (idx != -1) {
    val headerStr = s.input.slice(start, idx).utf8String
    val headers = parseHeaderLines(headerStr)

    val info = classify(headers, s.classifiers)

    // Check if content type suggests binary/large file
    val contentType = headers.getOrElse("content-type", "").toLowerCase
    val shouldStream = contentType.startsWith("application/pdf") ||
                      contentType.startsWith("application/octet-stream") ||
                      contentType.startsWith("image/") ||
                      contentType.startsWith("video/")

    info match {
      case r: RelatedPartInfo if shouldStream =>
        // Stream PDFs even in multipart/related
        val name = r.contentId
        Step(
          s.copy(phase = FileData(partStart, mem, name)),
          EmitPart(FilePart(name, s"file_${partCounter}", Some(contentType), ())) :: Nil
        )

      case r: RelatedPartInfo =>
        // Buffer small JSON/XML
        Step(s.copy(phase = DataBody(partStart, mem, r.contentId)), Nil)

      // ... rest of cases
    }
  }
```

#### Option B: Auto-Increase Buffer Based on Content-Length

```scala
// Check Content-Length header and auto-adjust buffer
val contentLength = headers.get("content-length").flatMap(_.toLongOption).getOrElse(0L)
val requiredBuffer = mem + contentLength

if (requiredBuffer > s.maxMem) {
  logger.warn(s"Part $name needs ${requiredBuffer} bytes but limit is ${s.maxMem}")

  // Option 1: Emit warning and continue (current behavior)
  // Option 2: Auto-stream to temp file
  // Option 3: Fail gracefully
}
```

#### Option C: Make Buffer Size Configurable Per Content-Type

```scala
case class MultipartParserConfig(
  boundary: String,
  maxHeaderSize: Int = 4096,
  bufferSizes: Map[String, Int] = Map(
    "application/json" -> 16384,      // 16KB for JSON
    "application/pdf"  -> 10485760,   // 10MB for PDFs
    "image/*"          -> 5242880,    // 5MB for images
    "*"                -> 16384,       // Default: 16KB
  )
)
```

### Recommended Solution

**For now**: Use the manual config approach (what we did)
```scala
.withParserConfig(MultipartParserConfig(
  boundary = "",
  maxMemoryBufferSize = 1024 * 1024,  // 1MB for shipping labels
))
```

**For production**: Implement Option A - automatically stream binary content types instead of buffering them.

---

## Issue #3: 403 Error with Multipart Builder vs 200 with Raw Request

### The Mystery

**Raw Request (works - 200 OK):**
```scala
wsClient
  .url("...")
  .addHttpHeaders("token" -> apiToken)
  .addHttpHeaders("Content-Type" -> "application/json")
  .post(Json.stringify(payload))  // String body
```

**Multipart Builder (fails - 403 Forbidden):**
```scala
Multipart.request(httpClient)
  .post("...")
  .withHeader("token", apiToken)
  .withJsonBody(payload)  // JsValue body
  .execute()
```

### Root Cause Analysis

The difference is in **how Play WS handles the body**:

#### Raw Request Flow:
1. `.post(String)` sends the JSON as a **string**
2. Uses default `BodyWritable[String]`
3. Headers sent:
   ```
   Content-Type: application/json
   token: xxx
   (Plus Play WS defaults)
   ```

#### Multipart Builder Flow:
1. `.withJsonBody(JsValue)` stores a `JsonBody(JsValue)`
2. `PlayWSHttpClient.setBody()` calls `req.withBody(json)` where `json: JsValue`
3. Uses `JsonBodyWritables._` implicit writer
4. Headers sent:
   ```
   Content-Type: application/json  (added by PlayWSHttpClient)
   token: xxx
   User-Agent: curl/8.7.1          (added by addDefaultsIfMissing)
   Accept: */*                     (added by addDefaultsIfMissing)
   Accept-Language: en-US,en;q=0.9
   Accept-Encoding: gzip, deflate, br
   ```

### The Likely Culprit

The Colissimo API server **might be rejecting requests** based on:

1. **Extra headers** - Some APIs reject requests with unexpected headers
2. **User-Agent** - Server might block "curl" user-agent
3. **Accept-Encoding** - Server might have issues with compression
4. **Charset in Content-Type** - The JsonBodyWritable might add `charset=utf-8`

### How to Debug

Add logging to see exact headers sent:

```scala
// In PlayWSHttpClient.scala, after buildRequest:
private def buildRequest(request: HttpRequest): StandaloneWSRequest = {
  val wsRequest = ...

  // DEBUG: Log all headers
  logger.debug(s"Request URL: ${wsRequest.url}")
  logger.debug(s"Request Method: ${wsRequest.method}")
  logger.debug("Request Headers:")
  wsRequest.headers.foreach { case (name, values) =>
    values.foreach(v => logger.debug(s"  $name: $v"))
  }

  wsRequest
}
```

### Quick Fixes to Try

#### Fix #1: Minimize Extra Headers

```scala
// In PlayWSHttpClient.scala
private def addDefaultsIfMissing(req: StandaloneWSRequest): StandaloneWSRequest = {
  // Don't add defaults for API calls - only add User-Agent if missing
  if (!req.headers.keys.exists(_.equalsIgnoreCase("User-Agent"))) {
    req.withHttpHeaders("User-Agent" -> "Scala-Multipart-Client/1.0")
  } else {
    req
  }
}
```

#### Fix #2: Use String Body Instead of JsValue

Change the builder to always stringify JSON:

```scala
// In PlayWSHttpClient.scala setBody method
case Some(JsonBody(json)) =>
  // Stringify the JSON instead of using JsValue writer
  val jsonString = play.api.libs.json.Json.stringify(json)
  val r = req.withBody(jsonString)
  if (hasContentType(request.headers)) r
  else r.withHttpHeaders("Content-Type" -> "application/json")
```

#### Fix #3: Match Raw Request Exactly

Don't add any default headers at all:

```scala
// Remove or disable addDefaultsIfMissing entirely
val withHeaders = applyHeaders(base2, request.headers)  // Skip addDefaultsIfMissing
```

### Testing

Create a test endpoint to dump all headers:

```scala
// Test what headers are actually sent
Multipart.request(httpClient)
  .post("https://httpbin.org/post")  // Echo service
  .withHeader("token", apiToken)
  .withJsonBody(payload)
  .execute()
  .map { result =>
    println("Headers echoed back:")
    // httpbin.org will show you exactly what was sent
  }
```

---

## Summary & Recommendations

### Issue #1: Boundary Detection ✅ SOLVED
- Current solution is generic and handles all cases
- No changes needed
- Works with both standard and non-standard multipart formats

### Issue #2: Buffer Size 🔄 WORKAROUND IN PLACE
- **Current**: Manual 1MB buffer configuration works
- **Better**: Implement content-type based streaming (future enhancement)
- **Best**: Stream all binary files, buffer only JSON/XML

### Issue #3: 403 Error 🔍 NEEDS INVESTIGATION
- **Root cause**: Different headers sent by builder vs raw request
- **Quick fix**: Use Fix #2 (stringify JSON) or Fix #3 (remove default headers)
- **Proper fix**: Add debug logging to see exact difference

### Next Steps

1. **For Issue #3** (most urgent):
   ```scala
   // Try this change in PlayWSHttpClient.scala:
   case Some(JsonBody(json)) =>
     val jsonString = play.api.libs.json.Json.stringify(json)
     req.withBody(jsonString)
       .withHttpHeaders("Content-Type" -> "application/json")
   ```

2. **Test** and compare headers with the working raw request

3. **For Issue #2** (future enhancement):
   - Implement content-type based streaming
   - Create configuration for buffer sizes per type

Would you like me to implement any of these fixes?
