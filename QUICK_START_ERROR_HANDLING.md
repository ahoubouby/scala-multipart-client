# Quick Start: JSON Error Handling

This library now handles **any** JSON error response from web services, regardless of structure.

## The Problem

You expect multipart response, but get JSON error:
```json
{"timestamp": 1761436501465, "status": 500, "error": "Internal Server Error"}
```

## The Solution

Three ways to handle it, from simplest to most flexible:

### 1. Quick & Easy (Auto Error Message)

```scala
.recover {
  case ex: NonMultipartResponseException if ex.isJsonError =>
    // Automatically extracts from: "error", "message", "errorMessage", "detail"
    println(s"Error: ${ex.errorMessage.getOrElse("Unknown")}")
}
```

### 2. Path-Based Access (Your API Structure)

```scala
.recover {
  case ex: NonMultipartResponseException if ex.isJsonError =>
    // Works with ANY structure - just specify the path
    val code = ex.getJsonField("error.code").flatMap(_.asOpt[String])
    val reason = ex.getJsonField("data.reason").flatMap(_.asOpt[String])

    println(s"Error code: $code, reason: $reason")
}
```

### 3. Full Flexibility (Play JSON)

```scala
.recover {
  case ex: NonMultipartResponseException if ex.isJsonError =>
    ex.jsonBody.foreach { json =>
      // Use full Play JSON API - works with ANY structure
      val myCustomField = (json \ "custom" \ "field").asOpt[String]
      val arrayItems = (json \ "items").as[Seq[JsValue]]

      // Your parsing logic here
    }
}
```

## Real Examples

### Example 1: Colissimo API (Your Case)
```scala
// Returns: {"timestamp": ..., "status": 500, "error": "...", "path": "..."}
case ex: NonMultipartResponseException =>
  ex.getJsonField("error").flatMap(_.asOpt[String]) // "Internal Server Error"
  ex.getJsonField("path").flatMap(_.asOpt[String])  // "/sls-ws/..."
  ex.toMap // Full flattened map for logging
```

### Example 2: AWS-Style Errors
```scala
// Returns: {"__type": "ValidationException", "message": "Invalid input"}
case ex: NonMultipartResponseException =>
  ex.getJsonField("__type").flatMap(_.asOpt[String])
  ex.errorMessage // Automatically finds "message" field
```

### Example 3: OAuth Errors
```scala
// Returns: {"error": "invalid_grant", "error_description": "Token expired"}
case ex: NonMultipartResponseException =>
  ex.errorMessage // Automatically finds "error_description"
  ex.getJsonField("error").flatMap(_.asOpt[String])
```

### Example 4: Nested Validation Errors
```scala
// Returns: {"errors": [{"field": "email", "code": "INVALID"}]}
case ex: NonMultipartResponseException =>
  ex.jsonBody.foreach { json =>
    (json \ "errors").as[Seq[JsValue]].foreach { error =>
      val field = (error \ "field").as[String]
      val code = (error \ "code").as[String]
      println(s"Field $field: $code")
    }
  }
```

## Debugging Tool

```scala
case ex: NonMultipartResponseException if ex.isJsonError =>
  // Quick way to see everything
  println(ex.toMap)
  // Output: Map("status" -> "500", "error" -> "...", "data.user" -> "john")
```

## Non-JSON Errors

```scala
case ex: NonMultipartResponseException if !ex.isJsonError =>
  // HTML, XML, plain text, etc.
  ex.bodyAsString.foreach(body => println(s"Raw: $body"))
```

## Key Points

1. **No assumptions** - works with any JSON structure
2. **Multiple access methods** - choose what works for you
3. **Type safe** - all methods return Option types
4. **Future proof** - if API changes format, just change the field paths

## Complete Example

```scala
import com.multipart.api.Multipart
import com.multipart.parser.NonMultipartResponseException

Multipart
  .request(httpClient)
  .post("/api/endpoint")
  .withJsonBody(requestData)
  .execute()
  .map { result =>
    // Success - process multipart response
    result.jsonParts
    result.pdfParts
  }
  .recover {
    case ex: NonMultipartResponseException if ex.isJsonError =>
      // JSON error - extract what you need
      val errorCode = ex.getJsonField("code")
      val errorMsg = ex.errorMessage

      logger.error(s"API returned error: $errorCode - $errorMsg")
      logger.debug(s"Full error: ${ex.toMap}")

      throw ex // or handle appropriately

    case ex: NonMultipartResponseException =>
      // Non-JSON error (HTML, etc.)
      logger.error(s"Unexpected response type: ${ex.contentType}")
      throw ex
  }
```

## See Also

- `docs/JSON_ERROR_HANDLING.md` - Full documentation with more examples
- `examples/shipping-labels/` - Complete working example
- `src/test/scala/.../JsonErrorResponseSpec.scala` - Test examples
