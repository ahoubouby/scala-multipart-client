# Debugging HTTP 403 Issue

## Problem

Curl command returns **HTTP 200** with valid multipart response, but the Scala client returns **HTTP 403 Forbidden**.

## Curl Working Example

```bash
curl -X POST "https://qualification.colissimo.fr/sls-ws/SlsServiceRest/SlsInternalService/generateLabel" \
  -H "Content-Type: application/json" \
  -H "token: 7e13ce23fa232b3fff19480e6fb12c00" \
  -d '{...}' -v
```

**Response**: HTTP 200 with multipart/related containing error message about deposit date.

## What to Check

### 1. Run the Client with Logging

```bash
cd /path/to/scala-multipart-client
sbt "examples/runMain example.ShippingLabelClient"
```

### 2. Compare HTTP Headers

The logs will show:

```
[INFO] HTTP POST https://qualification.colissimo.fr/sls-ws/SlsServiceRest/SlsInternalService/generateLabel
[DEBUG] Request headers:
  Content-Type: application/json
  token: 7e13ce23fa...
[DEBUG] Request body (JSON): {"outputFormat":...

[INFO] HTTP 403 Forbidden
[WARN] Non-successful HTTP status: 403 Forbidden
```

### 3. Common Causes of 403

#### A. User-Agent Header

Some APIs reject requests without a User-Agent.

**Solution**: Add User-Agent header

In `ShippingLabelClient.scala`, modify the request:

```scala
Multipart
  .request(httpClient)
  .post("/sls-ws/SlsServiceRest/SlsInternalService/generateLabel")
  .withHeader("token", apiToken)
  .withHeader("User-Agent", "Scala-Multipart-Client/0.1.0")  // Add this
  .withJsonBody(payload)
  .execute()
```

#### B. Accept Header

Some APIs require specific Accept headers.

**Solution**: Add Accept header

```scala
  .withHeader("Accept", "*/*")  // Or "multipart/related"
```

#### C. Host Header

Play WS should handle this automatically, but verify in logs.

#### D. SSL/TLS Version

The client might be using a different TLS version than curl.

**Check**: Look for SSL errors in the full stack trace.

#### E. IP Whitelisting / Rate Limiting

The API might have different rules for different clients.

### 4. Full Request Comparison

Create a test file to see exact curl headers:

```bash
curl -X POST "https://qualification.colissimo.fr/sls-ws/SlsServiceRest/SlsInternalService/generateLabel" \
  -H "Content-Type: application/json" \
  -H "token: 7e13ce23fa232b3fff19480e6fb12c00" \
  -d @payload.json \
  -v 2>&1 | tee curl-headers.txt
```

Compare with client logs:
- Headers sent
- Header order (some APIs care!)
- Header casing
- Body encoding

### 5. Quick Fixes to Try

#### Fix 1: Add Common Headers

```scala
Multipart
  .request(httpClient)
  .post("/sls-ws/SlsServiceRest/SlsInternalService/generateLabel")
  .withHeader("token", apiToken)
  .withHeader("User-Agent", "curl/7.68.0")        // Mimic curl
  .withHeader("Accept", "*/*")                     // Common accept
  .withJsonBody(payload)
  .execute()
```

#### Fix 2: Check Token Format

Ensure the token is exactly what curl uses:

```scala
// BAD: Extra whitespace or encoding
val apiToken = " 7e13ce23fa232b3fff19480e6fb12c00"  // Leading space!

// GOOD: Exact match
val apiToken = "7e13ce23fa232b3fff19480e6fb12c00"
```

#### Fix 3: Try Different Content-Type

```scala
  .withHeader("Content-Type", "application/json; charset=utf-8")
```

#### Fix 4: Verify Date Format

The payload has `depositDate: "2025-10-27"`. Ensure this is a valid future date:

```scala
// Get today + 2 days
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val depositDate = LocalDate.now().plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE)
// Update payload with this date
```

### 6. Enable Netty/AHC Debug Logging

To see even more details, create `examples/src/main/resources/logback.xml`:

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

  <!-- Client logging -->
  <logger name="com.multipart.client" level="DEBUG"/>
  <logger name="com.multipart.parser" level="DEBUG"/>

  <!-- HTTP client logging -->
  <logger name="play.shaded.ahc.org.asynchttpclient" level="DEBUG"/>
</configuration>
```

### 7. Check Response Body on 403

Even though it's a 403, the server might send useful error info.

Add this to `ShippingLabelClient.scala`:

```scala
result.onComplete {
  case Failure(exception) =>
    println(s"\n✗ Failed: ${exception.getMessage}")
    exception.printStackTrace()

    // Try to get response body if available
    exception match {
      case ex: Exception =>
        println("\nFull exception details:")
        println(ex.toString)
    }
    shutdown()
}
```

### 8. Test with Postman/Insomnia

To isolate the issue:
1. Import the curl command into Postman/Insomnia
2. Verify it works
3. Compare headers Postman sends vs what curl sends
4. Replicate those headers in the Scala client

### 9. Network Capture

Use Wireshark or `tcpdump` to see the actual bytes sent:

```bash
# Capture traffic to Colissimo
sudo tcpdump -i any -A host qualification.colissimo.fr -w colissimo.pcap

# Then run both curl and the Scala client
# Compare the captures
```

## Expected Working Logs

When it works, you should see:

```
Colissimo Shipping Label Client
==================================================
API Base URL: https://qualification.colissimo.fr
API Token: 7e13ce23fa...
==================================================

📤 Sending request to Colissimo API...
   Endpoint: POST /sls-ws/SlsServiceRest/SlsInternalService/generateLabel
   Headers:
     - Content-Type: application/json
     - token: 7e13ce23fa...
   Payload size: 2206 bytes

[INFO] HTTP POST https://qualification.colissimo.fr/sls-ws/SlsServiceRest/SlsInternalService/generateLabel
[INFO] HTTP 200 OK
[INFO] Content-Type: multipart/related; type="application/json"; boundary="uuid:..."

📥 Response received successfully
   Multipart format: multipart/related
   Boundary: uuid:6f8dc102-29b8-4d16-accb-e6d5de31ef43
   Number of parts: 1

✓ Successfully received multipart response

==================================================
MULTIPART RESPONSE ANALYSIS
==================================================
Format: multipart/related
Boundary: uuid:6f8dc102-29b8-4d16-accb-e6d5de31ef43
Total parts: 1
--------------------------------------------------

📦 Part 1/1
   Identifier: <jsonInfos>
   Content-Type: application/json;charset=UTF-8
   Size: 184 B
   Type flags: JSON=true, PDF=false, Image=false
   Content-ID: <jsonInfos>

--------------------------------------------------

📋 JSON Metadata (<jsonInfos>)
--------------------------------------------------
{
  "messages" : [ {
    "id" : "30002",
    "type" : "ERROR",
    "messageContent" : "La date de dépôt est antérieure à la date courante",
    "replacementValues" : [ ]
  } ],
  "parcelNumber" : null,
  ...
}

⚠️  API Messages:
   [ERROR] (ID: 30002) La date de dépôt est antérieure à la date courante

⚠️  No PDF labels in response

==================================================
SUMMARY
==================================================
  JSON parts:  1
  PDF parts:   0
  Image parts: 0
  Total parts: 1
==================================================
```

## Next Steps

1. **Pull latest code** and run the client
2. **Compare logs** with curl output
3. **Try the quick fixes** above (User-Agent, Accept headers)
4. **Check the date** in the payload
5. **Share the full logs** if issue persists

## Key Point

The fact that curl gets **200** but client gets **403** means:
- ✅ Authentication token is valid
- ✅ API endpoint is correct
- ✅ Payload is correct
- ❌ Something about the HTTP request format differs

Most likely culprits:
1. Missing `User-Agent` header
2. Missing `Accept` header
3. Header ordering/casing issues
4. Request body encoding

The logging will reveal the exact difference!
