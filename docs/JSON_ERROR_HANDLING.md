# JSON Error Response Handling

## Problem

The multipart parser library was designed to handle multipart responses (multipart/form-data, multipart/related, etc.). However, some APIs may return JSON error responses instead of multipart when errors occur, particularly for HTTP error status codes (4xx, 5xx).

### Example Scenario

When calling the Colissimo shipping label API with invalid credentials or malformed requests:

**Expected Response:**
```
Content-Type: multipart/related; boundary="uuid:..."
```

**Actual Response (on error):**
```
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "timestamp": 1761436501465,
  "status": 500,
  "error": "Internal Server Error",
  "path": "/sls-ws/SlsServiceRest/SlsInternalService/generateLabel"
}
```

### Previous Behavior

The library would throw a generic exception:
```
Not a multipart response. Content-Type: application/json
```

This made it difficult to:
- Understand what went wrong
- Extract error details from the API
- Provide meaningful feedback to users

## Solution

### 1. New Exception Type

Created `NonMultipartResponseException` that:
- Captures the HTTP status code
- Stores the Content-Type header
- Optionally parses and stores JSON error bodies
- Provides helper methods to extract error information

```scala
case class NonMultipartResponseException(
  contentType: String,
  status: Int,
  jsonBody: Option[JsValue] = None
)
```

### 2. JSON Error Detection

Enhanced `MultipartParser` to:
1. Detect when Content-Type is `application/json`
2. Read and parse the response body
3. Include parsed JSON in the exception
4. Log the error details for debugging

### 3. Error Information Extraction

The exception provides convenient methods:

```scala
val ex: NonMultipartResponseException = ...

// Check if it's a JSON error
ex.isJsonError // true if jsonBody is defined

// Get error message (checks both "error" and "message" fields)
ex.errorMessage // Option[String]

// Get all error details as a map
ex.errorDetails // Map[String, String]
// Returns: status, error, message, path, timestamp
```

### 4. Updated Example

The ShippingLabelClient example now demonstrates proper error handling:

```scala
result.onComplete {
  case Success(multipart) =>
    processMultipartResponse(multipart)

  case Failure(exception: NonMultipartResponseException) =>
    handleJsonError(exception)

  case Failure(exception) =>
    // Handle other exceptions
}
```

## Usage

### Basic Error Handling

```scala
import com.multipart.parser.NonMultipartResponseException

Multipart
  .request(httpClient)
  .post("/api/endpoint")
  .execute()
  .recover {
    case ex: NonMultipartResponseException if ex.isJsonError =>
      println(s"API Error: ${ex.errorMessage.getOrElse("Unknown error")}")
      println(s"Status: ${ex.status}")
      // Handle the error appropriately
      throw ex
  }
```

### Detailed Error Information

```scala
case ex: NonMultipartResponseException if ex.isJsonError =>
  val details = ex.errorDetails

  details.get("error").foreach(err => println(s"Error: $err"))
  details.get("message").foreach(msg => println(s"Message: $msg"))
  details.get("path").foreach(path => println(s"Path: $path"))

  // Access raw JSON for custom parsing
  ex.jsonBody.foreach { json =>
    val customField = (json \ "customField").asOpt[String]
  }
```

### Example Output

When an API returns a JSON error:

```
✗ API returned error response instead of multipart

==================================================
ERROR RESPONSE DETAILS
==================================================
HTTP Status: 500
Content-Type: application/json

JSON Error Body:
--------------------------------------------------
{
  "timestamp" : 1761436501465,
  "status" : 500,
  "error" : "Internal Server Error",
  "path" : "/sls-ws/SlsServiceRest/SlsInternalService/generateLabel"
}

Error Message: Internal Server Error

Error Details:
  status: 500
  error: Internal Server Error
  path: /sls-ws/SlsServiceRest/SlsInternalService/generateLabel
  timestamp: 1761436501465
==================================================
```

## Testing

Added comprehensive unit tests in `JsonErrorResponseSpec`:

- ✓ JSON error responses with complete error information
- ✓ Extraction of error messages from multiple field names
- ✓ Handling of malformed JSON gracefully
- ✓ Error details map population
- ✓ Empty JSON objects
- ✓ Non-JSON error responses (HTML, plain text)
- ✓ Successful multipart responses (no regression)

## Migration Guide

### If you were catching generic exceptions:

**Before:**
```scala
.recover {
  case ex: Exception =>
    println(s"Error: ${ex.getMessage}")
}
```

**After:**
```scala
.recover {
  case ex: NonMultipartResponseException =>
    if (ex.isJsonError) {
      // Handle JSON error with detailed information
      ex.errorMessage.foreach(msg => println(s"API Error: $msg"))
    } else {
      // Handle non-JSON error
      println(s"Error: ${ex.contentType}")
    }
  case ex: Exception =>
    println(s"Error: ${ex.getMessage}")
}
```

## Benefits

1. **Better Error Messages**: Users see actual API error messages instead of generic parsing failures
2. **Easier Debugging**: Developers can access complete error details from JSON responses
3. **Graceful Degradation**: Handles malformed JSON without crashing
4. **Backward Compatible**: Doesn't break existing multipart response handling
5. **Type Safe**: Strongly typed exception with helper methods

## Related Files

- `src/main/scala/com/multipart/parser/MultipartException.scala` - Exception definitions
- `src/main/scala/com/multipart/parser/MultipartParser.scala` - JSON detection and parsing
- `src/test/scala/com/multipart/parser/JsonErrorResponseSpec.scala` - Comprehensive tests
- `examples/shipping-labels/src/main/scala/example/ShippingLabelClient.scala` - Usage example
