# Troubleshooting Guide

## Common Issues and Solutions

### 1. IllegalArgumentException: Cannot pull closed port

**Symptoms:**
```
java.lang.IllegalArgumentException: Cannot pull closed port (GenericBodyPartParser.in(...))
  at com.multipart.parser.GenericBodyPartParser$$anon$1.drive(GenericBodyPartParser.scala:360)
  at com.multipart.parser.GenericBodyPartParser$$anon$1.onUpstreamFinish(GenericBodyPartParser.scala:323)
```

**Cause:**
This error occurs when the HTTP response stream ends prematurely while the multipart parser is still expecting more data. Common scenarios:

1. **HTTP Error Responses (403, 500, etc.)**
   - The server returns an error status code
   - The Content-Type header claims `multipart/*`
   - But the body is empty, truncated, or malformed

2. **Network Issues**
   - Connection drops before transfer completes
   - Timeout occurs mid-transfer
   - Server kills connection

3. **Malformed Multipart Data**
   - Missing closing boundary
   - Incomplete headers
   - Truncated body content

**Solution (Fixed in v0.1.1+):**

The library now gracefully handles incomplete multipart data:

```scala
// The parser tracks upstream state and won't pull from closed streams
private var upstreamFinished = false

override def onUpstreamFinish(): Unit = {
  upstreamFinished = true
  // Process remaining buffered data
  if (state.input.nonEmpty && !finished) drive()

  // Emit error if data is incomplete
  if (!finished && state.input.nonEmpty) {
    queue = queue.enqueue(Left(ParseError("Incomplete multipart data: ...")))
  }
  completeStage()
}
```

**What You'll See Now:**
- A warning log about incomplete data
- The parser returns a `ParseError` in the results
- No crash - the stream completes gracefully

**How to Handle in Your Code:**
```scala
val result = Multipart.request(httpClient)
  .post("/api/endpoint")
  .execute()

result.map { multipartResult =>
  multipartResult.parts.foreach {
    case part if part.identifier.contains("error") =>
      // Check for parse errors
      val errorMsg = part.asString
      logger.warn(s"Multipart parsing issue: $errorMsg")
    case part =>
      // Normal processing
      processPart(part)
  }
}
```

**Prevention:**

1. **Check HTTP Status Codes:**
```scala
// The library warns about non-2xx status codes:
// [WARN] Parsing multipart response with non-successful status 403.
//        The response body may be incomplete, empty, or contain error messages.
```

2. **Validate Responses Before Parsing:**
```scala
if (response.status >= 400) {
  // Handle error response separately
  val errorBody = response.bodyAsString
  throw new ApiException(s"API error ${response.status}: $errorBody")
}
```

3. **Add Timeout Handling:**
```scala
Multipart.request(httpClient)
  .post("/api/endpoint")
  .withTimeout(30.seconds)  // Prevent indefinite hangs
  .execute()
```

---

### 2. Empty Multipart Results

**Symptoms:**
- `MultipartResult.parts` is empty
- No errors thrown
- Logs show "Detected format: multipart/related, boundary: ..."

**Possible Causes:**

1. **Server Sent Empty Body**
   - Check server logs/debugging
   - Verify API authentication is correct
   - Check request payload is valid

2. **Incorrect Boundary Detection**
   - The detected boundary doesn't match actual data
   - Check Content-Type header format

3. **Encoding Issues**
   - Non-UTF8 data in text parts
   - Binary data corruption

**Debugging:**

Enable DEBUG logging to see raw parsing:
```scala
// In logback.xml
<logger name="com.multipart.parser" level="DEBUG"/>
```

You'll see:
```
[DEBUG] Boundary extracted: 'uuid:abc-123' (length: 12)
[DEBUG] Format identified as: multipart/related
[DEBUG] Using parser config: maxMemoryBuffer=16384, maxHeaderSize=4096
```

**Solution:**

1. Inspect the raw response:
```scala
val response = httpClient.execute(request)
val rawBody = response.bodyAsString  // Be careful with large responses
println(s"Raw response body:\n$rawBody")
```

2. Verify boundary format:
```scala
val contentType = response.header("content-type")
println(s"Content-Type: $contentType")
// Should match boundary in actual data
```

---

### 3. Memory Buffer Exceeded

**Symptoms:**
```
MaxMemoryBufferExceeded: Part data exceeds memory buffer limit
```

**Cause:**
A single multipart part is larger than `maxMemoryBufferSize` (default: 16KB)

**Solution:**

Increase buffer size for large files:
```scala
val config = MultipartParserConfig(
  boundary = "",  // Auto-detected
  maxMemoryBufferSize = 1024 * 1024,  // 1MB
  maxHeaderSize = 8192,
)

Multipart.request(httpClient)
  .post("/api/endpoint")
  .withParserConfig(config)
  .execute()
```

**Note:** For very large files (>10MB), consider streaming-based processing instead of loading into memory.

---

### 4. Netty/Pekko Debug Logs

**Symptoms:**
Excessive DEBUG logs from `play.shaded.ahc.io.netty`:
```
[DEBUG] Platform: MacOS
[DEBUG] sun.misc.Unsafe.theUnsafe: available
[DEBUG] -Dio.netty.allocator.type: pooled
```

**Solution:**

Reduce Netty logging in `src/main/resources/logback.xml`:
```xml
<configuration>
  <logger name="play.shaded.ahc.io.netty" level="WARN"/>
  <logger name="org.apache.pekko" level="INFO"/>

  <root level="INFO">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

---

### 5. Test Failures with Resource Files

**Symptoms:**
```
NotEnoughDataException: Not enough data
  at BoyerMooreSpec: "find multipart boundary with CRLF prefix"
```

**Cause:**
Resource files have Unix line endings (LF) instead of Windows/HTTP line endings (CRLF)

**Solution:**

Ensure test resource files use CRLF (`\r\n`):
```bash
# Convert to CRLF
sed -i 's/$/\r/' src/test/resources/multipart/*.txt

# Verify
od -c src/test/resources/multipart/simple-form-data.txt | grep '\\r'
```

---

## Getting Help

If you encounter issues not covered here:

1. **Enable Debug Logging:**
   ```xml
   <logger name="com.multipart" level="DEBUG"/>
   ```

2. **Check the Logs for:**
   - HTTP status codes
   - Detected boundary
   - Parser phase when error occurred
   - Buffer sizes

3. **Report Issues:**
   - GitHub: https://github.com/your-org/scala-multipart-client/issues
   - Include:
     - Error message and stack trace
     - HTTP status code
     - Content-Type header
     - Sample of response body (sanitized)
     - Library version

4. **Review Examples:**
   - See `examples/` directory for working code
   - Run the mock server for local testing
