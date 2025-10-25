# Examples

This directory contains runnable examples demonstrating how to use the Scala Multipart Client library.

## Available Examples

### 1. ShippingLabelClient

A complete example showing how to:
- Make HTTP requests that return multipart responses
- Parse multipart/related responses with JSON metadata and PDF labels
- Extract and save different parts (JSON, PDF, images)
- Use helper methods to identify content types

**File**: `src/main/scala/example/ShippingLabelClient.scala`

### 2. MockShippingServer

A mock HTTP server that simulates a shipping label API for testing purposes.

**File**: `src/main/scala/example/MockShippingServer.scala`

## Running the Examples

### Option 1: With a Real API

If you have access to a shipping label API:

```bash
# Set environment variables
export SHIPPING_API_URL="https://api.your-shipping-service.com"
export SHIPPING_API_TOKEN="your-api-token"
export OUTPUT_DIR="./output"

# Run the client
sbt "examples/runMain example.ShippingLabelClient"
```

### Option 2: With Mock Server (Recommended for Testing)

1. **Start the mock server** (in terminal 1):
```bash
sbt "examples/runMain example.MockShippingServer"
```

This will start a server at `http://localhost:8080`

2. **Run the client** (in terminal 2):
```bash
# Configure to use local mock server
export SHIPPING_API_URL="http://localhost:8080"
export SHIPPING_API_TOKEN="test-token"
export OUTPUT_DIR="./output"

# Run the client
sbt "examples/runMain example.ShippingLabelClient"
```

The client will:
- Connect to the mock server
- Request a shipping label
- Parse the multipart response
- Display metadata
- Save PDF and image files to the output directory

## Understanding the Output

When you run the `ShippingLabelClient`, you'll see output like:

```
==================================================
Shipping Label Client Example
==================================================

Requesting label for parcel: PKG-12345
Destination: New York, NY

✓ Received multipart response
  Format: multipart/related
  Parts: 2

Part: <metadata>
  Content-Type: application/json
  Size: 423 B
  Content-ID: <metadata>
  Detected: JSON

Part: <label>
  Content-Type: application/pdf
  Size: 1.23 KB
  Content-ID: <label>
  Detected: PDF

==================================================
Metadata Parts
==================================================
Part: <metadata>
{
  "parcelNumber" : "PKG-12345",
  "trackingNumber" : "1Z999AA10123456784",
  "service" : "express",
  ...
}

📦 Tracking Number: 1Z999AA10123456784
🚚 Service: express

==================================================
Saving PDF Labels
==================================================
✓ Saved: label.pdf (1.23 KB)
  ✓ Valid PDF format

==================================================
✓ Processing completed successfully
Output directory: ./output
==================================================
```

## Key Features Demonstrated

### 1. Enhanced MultipartPart Methods

The examples showcase the enhanced `MultipartPart` class with helpful methods:

```scala
// Content type detection
part.isPdf       // Check if PDF
part.isJson      // Check if JSON
part.isImage     // Check if image
part.isXml       // Check if XML
part.isExcel     // Check if Excel/spreadsheet
part.isCsv       // Check if CSV

// Size helpers
part.size           // Size in bytes
part.sizeKB         // Size in KB
part.sizeMB         // Size in MB
part.sizeFormatted  // Human-readable (e.g., "1.23 KB")

// Metadata access
part.filename          // Get filename (for form-data)
part.contentId         // Get content-id (for related)
part.contentLocation   // Get content-location
part.headers           // All headers as Map

// Validation
part.isValidPdf    // Check PDF magic bytes
part.imageFormat   // Detect image format (JPEG, PNG, GIF)

// Data access
part.asString           // Get as UTF-8 string
part.asString("UTF-16") // Get with custom encoding
```

### 2. Working with MultipartResult

```scala
result.parts           // All parts
result.jsonParts       // Filter JSON parts
result.pdfParts        // Filter PDF parts
result.imageParts      // Filter image parts
result.xmlParts        // Filter XML parts

result.getPart("identifier")              // Get by exact ID
result.getPartsMatching(".*label.*")      // Get by regex
result.getPartsByContentType("pdf")       // Get by content type
```

### 3. Error Handling

```scala
// The example shows proper error handling:
resultFuture
  .map { result =>
    // Process successful response
    processParts(result)
  }
  .recover {
    case ex: Exception =>
      logger.error("Failed to process", ex)
      // Handle error
  }
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SHIPPING_API_URL` | API base URL | `https://api.shipping.example.com` |
| `SHIPPING_API_TOKEN` | API authentication token | `your-api-token-here` |
| `OUTPUT_DIR` | Directory to save files | `./output` |

## Troubleshooting

### Debug Logging

The examples use Logback for logging. You may see DEBUG logs from Netty (the underlying HTTP client), which is normal. These logs show:
- Platform detection
- Memory allocation
- Network configuration

To reduce logging, create `examples/src/main/resources/logback.xml`:

```xml
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="STDOUT" />
  </root>

  <!-- Reduce Netty logging -->
  <logger name="play.shaded.ahc.io.netty" level="WARN"/>
</configuration>
```

### Common Issues

1. **Connection refused**: Make sure the mock server is running if using local testing
2. **Invalid API token**: Check your `SHIPPING_API_TOKEN` environment variable
3. **File save errors**: Ensure the `OUTPUT_DIR` exists and is writable

## Adapting the Examples

### Using with Your API

1. Update the request endpoint in `ShippingLabelClient.scala`:
```scala
val result = Multipart.request(httpClient)
  .post("/your/api/endpoint")  // Change this
  .withAuth(apiToken)
  .withJsonBody(yourPayload)   // Update payload
  .execute()
```

2. Adjust the response processing based on your API's multipart structure

### Custom Part Processing

Add custom processing for specific content types:

```scala
// Process Excel files
result.parts.filter(_.isExcel).foreach { part =>
  val filename = s"data-${part.identifier}.xlsx"
  Files.write(Paths.get(outputDir, filename), part.data)
  println(s"Saved Excel: $filename")
}

// Process CSV files
result.parts.filter(_.isCsv).foreach { part =>
  val content = part.asString
  processCsvData(content)
}
```

## Learn More

- See the [main README](../README.md) for library documentation
- Check [ARCHITECTURE.md](../docs/ARCHITECTURE.md) for design details
- Browse the test suite in `src/test/scala/` for more examples
