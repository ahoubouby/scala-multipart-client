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
- Stores the raw response body
- Optionally parses and stores JSON error bodies as `JsValue`
- Provides generic helper methods to extract data from **any** JSON structure

```scala
case class NonMultipartResponseException(
  contentType: String,
  status: Int,
  jsonBody: Option[JsValue] = None,
  rawBody: Option[Array[Byte]] = None
)
```

**Key Design Principle**: The exception is **structure-agnostic**. It doesn't assume any particular JSON format, making it work with any API.

### 2. JSON Error Detection

Enhanced `MultipartParser` to:
1. Detect when Content-Type is `application/json`
2. Read and parse the response body
3. Include parsed JSON in the exception
4. Log the error details for debugging

### 3. Generic Error Information Extraction

The exception provides flexible methods that work with **any JSON structure**:

```scala
val ex: NonMultipartResponseException = ...

// Always available: raw JsValue for maximum flexibility
ex.jsonBody // Option[JsValue] - work with ANY JSON structure

// Always available: raw body as bytes or string
ex.rawBody // Option[Array[Byte]]
ex.bodyAsString // Option[String]

// Generic field extraction using JSON path
ex.getJsonField("error.code") // Option[JsValue]
ex.getJsonField("data.details.reason") // Option[JsValue]

// Convenience: automatic error message extraction
// Tries: "error", "message", "errorMessage", "detail", "error_description"
ex.errorMessage // Option[String]

// Convenience: flatten entire JSON to Map[String, String] for logging
ex.toMap // Map[String, String]
// Example: Map("status" -> "500", "error.code" -> "ERR_AUTH", "data.user" -> "john")
```

**These methods work with ANY API** - you're not limited to a specific JSON schema!

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

### Basic Error Handling (Works with Any API)

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
      // Access the full JSON for your specific API structure
      ex.jsonBody.foreach { json =>
        println(s"Full error: ${Json.prettyPrint(json)}")
      }
      throw ex
  }
```

### Example 1: Standard REST API Errors

```scala
// API returns: {"status": 400, "message": "Invalid request", "code": "BAD_REQUEST"}
case ex: NonMultipartResponseException if ex.isJsonError =>
  ex.getJsonField("code").flatMap(_.asOpt[String]) match {
    case Some("BAD_REQUEST") => // Handle bad request
    case Some("UNAUTHORIZED") => // Handle auth error
    case _ => // Handle other errors
  }
```

### Example 2: Nested Error Structures

```scala
// API returns: {"error": {"type": "validation", "details": {"field": "email"}}}
case ex: NonMultipartResponseException if ex.isJsonError =>
  val errorType = ex.getJsonField("error.type").flatMap(_.asOpt[String])
  val field = ex.getJsonField("error.details.field").flatMap(_.asOpt[String])

  println(s"Validation error on field: ${field.getOrElse("unknown")}")
```

### Example 3: Array of Errors

```scala
// API returns: {"errors": [{"field": "email", "message": "Invalid"}]}
case ex: NonMultipartResponseException if ex.isJsonError =>
  ex.jsonBody.foreach { json =>
    (json \ "errors").asOpt[Seq[JsValue]].foreach { errors =>
      errors.foreach { error =>
        val field = (error \ "field").asOpt[String]
        val msg = (error \ "message").asOpt[String]
        println(s"Error in $field: $msg")
      }
    }
  }
```

### Example 4: Quick Debugging with toMap

```scala
case ex: NonMultipartResponseException if ex.isJsonError =>
  // Flatten entire JSON structure for logging
  val allFields = ex.toMap
  logger.error(s"API Error - all fields: $allFields")
  // Output: Map("status" -> "500", "error.code" -> "DB_ERROR", "error.message" -> "Connection failed")
```

### Example 5: Non-JSON Errors (HTML, XML, etc.)

```scala
case ex: NonMultipartResponseException if !ex.isJsonError =>
  ex.bodyAsString.foreach { body =>
    logger.error(s"Non-JSON error response: ${body.take(500)}")
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

- ✓ Standard JSON error responses (timestamp, status, error, path)
- ✓ Nested JSON structures with `getJsonField`
- ✓ Custom/non-standard JSON formats (proves it works with any structure)
- ✓ Automatic error message extraction from common field names
- ✓ JSON to Map conversion with `toMap` (flattening)
- ✓ Handling of malformed JSON gracefully
- ✓ Empty JSON objects
- ✓ Non-JSON error responses (HTML, plain text) with `bodyAsString`
- ✓ Raw body access for custom parsing
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

1. **Works with ANY API**: No assumptions about JSON structure - works with any error format
2. **Multiple Access Patterns**:
   - Raw `JsValue` for Play JSON operations
   - `getJsonField` for simple path-based access
   - `toMap` for quick logging/debugging
   - `bodyAsString` for non-JSON responses
3. **Better Error Messages**: Users see actual API error messages instead of generic parsing failures
4. **Easier Debugging**: Developers can access complete error details from JSON responses
5. **Graceful Degradation**: Handles malformed JSON without crashing
6. **Backward Compatible**: Doesn't break existing multipart response handling
7. **Type Safe**: Strongly typed exception with flexible extraction methods
8. **Future Proof**: If an API changes its error structure, your code still works (just access different fields)

## Related Files

- `src/main/scala/com/multipart/parser/MultipartException.scala` - Exception definitions
- `src/main/scala/com/multipart/parser/MultipartParser.scala` - JSON detection and parsing
- `src/test/scala/com/multipart/parser/JsonErrorResponseSpec.scala` - Comprehensive tests
- `examples/shipping-labels/src/main/scala/example/ShippingLabelClient.scala` - Usage example
