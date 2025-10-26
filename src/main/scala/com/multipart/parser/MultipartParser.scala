package com.multipart.parser

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import com.multipart.client._
import com.multipart.model._
import com.multipart.parser.Part.RawPart

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.util.ByteString
import play.api.libs.json.Json

/**
 * High-level multipart parser with Pekko Streams
 */
object MultipartParser extends LazyLogging {

  /**
   * Parse multipart response automatically detecting format
   */
  def parse(
    response: HttpResponse,
    config: Option[MultipartParserConfig] = None,
  )(implicit mat: Materializer,
    ec: ExecutionContext,
  ): Future[MultipartResult] = {

    logger.info(s"Starting multipart parse for response with status ${response.status}")

    // Warn about non-successful status codes
    if (response.status < 200 || response.status >= 300) {
      logger.warn(
        s"Parsing multipart response with non-successful status ${response.status}. " +
          s"The response body may be incomplete, empty, or contain error messages.",
      )
    }

    if (response.status < 400 || response.status >= 500) {
      logger.warn(
        s"Parsing multipart response with non-successful status ${response.status}. " +
          s"The response body may be incomplete, empty, or contain error messages.",
      )
    }

    // Validate it's multipart, handle JSON error responses
    if (!response.isMultipart) {
      val contentType = response.header("content-type").getOrElse("unknown")
      logger.error(s"Not a multipart response. Content-Type: $contentType")

      // Read the response body for better error reporting
      return response.bodyAsSource
        .runFold(ByteString.empty)(_ ++ _)
        .flatMap { bytes =>
          val rawBytes = bytes.toArray

          // If it's a JSON response, try to parse it
          val jsonBody = if (contentType.toLowerCase.contains("application/json")) {
            Try(Json.parse(rawBytes)).toOption.map { json =>
              logger.error(s"JSON error response: ${Json.prettyPrint(json)}")
              json
            }
          } else {
            None
          }

          Future.failed(
            NonMultipartResponseException(
              contentType = contentType,
              status = response.status,
              jsonBody = jsonBody,
              rawBody = Some(rawBytes)
            )
          )
        }
    }

    // Detect format and extract configuration
    val detected = FormatDetector.detect(response)
    logger.info(s"Detected format: ${detected.format.name}, boundary: ${detected.boundary}")

    val parserConfig = config.getOrElse(detected.toParserConfig())
    logger.info(
      s"Using parser config: maxMemoryBuffer=${parserConfig.maxMemoryBufferSize}, " +
        s"maxHeaderSize=${parserConfig.maxHeaderSize}",
    )

    // Parse the stream
    parseStream(response.bodyAsSource, parserConfig, detected)
  }

  /**
   * Parse a multipart HTTP stream into a structured result.
   *
   * Pipeline (high-level):
   *   ByteString
   *     ──► GenericBodyPartParser ──► RawPart (Either[Part[Unit], ByteString])
   *     ──► splitWhen(_.isLeft)                   // each substream is one multipart part
   *     ──► prefixAndTail(1)                      // (Seq[RawPart(head)], Source[RawPart]) head = metadata (Left(Part))
   *     ──► map(reconstruct)                      // Part[Source[ByteString,_]] (files stream; data stays materialized)
   *     ──► concatSubstreams                      // back to a single stream
   *     ──► mapAsync(processPart)                 // turn each part into (key, processedData)
   *     ──► Sink.seq                              // collect all parts
   */
  private def parseStream(
    source: Source[ByteString, _],
    config: MultipartParserConfig,
    detected: DetectedFormat,
  )(implicit mat: Materializer,
    ec: ExecutionContext,
  ): Future[MultipartResult] = {

    logger.info("Building parsing flow with splitWhen/prefixAndTail composition")

    // Step 1: raw parsing (ByteString → RawPart)
    val parseRawParts: Flow[ByteString, RawPart, _] =
      Flow[ByteString]
        .via(new GenericBodyPartParser(config))
        .log("raw-parts", part => s"Raw part: $part")
        .withAttributes(
          Attributes.logLevels(
            onElement = Attributes.LogLevels.Debug,
            onFinish = Attributes.LogLevels.Info,
            onFailure = Attributes.LogLevels.Error,
          ),
        )

    // Small pure helper to reconstruct a part from (prefix, tail)
    // prefix should be Seq(Left(Part[Unit])); tail is the body stream of Right(ByteString) chunks
    val reconstruct: ((Seq[RawPart], Source[RawPart, _])) => Part[Source[ByteString, _]] = {
      case (Seq(Left(file: FilePart[_])), body) =>
        // Stream the file body: keep only Right(bytes) from the tail
        val bytesSrc: Source[ByteString, _] =
          body.collect {
            case Right(bs) => bs
          }
        file.copy[Source[ByteString, _]](ref = bytesSrc)

      case (Seq(Left(otherPart)), body) =>
        // For non-file parts, body was already fully materialized upstream.
        // Drain/cancel the tail to avoid back-pressure leaks.
        body.runWith(Sink.ignore)
        otherPart.asInstanceOf[Part[Source[ByteString, _]]] // ref is unused for data parts

      case (prefix, _) =>
        logger.warn(s"Unexpected prefix structure in part reconstruction: $prefix")
        // Model a parse error as a synthetic Part (if you have a dedicated error part type, use it here)
        ParseError("Unexpected part structure").asInstanceOf[Part[Source[ByteString, _]]]
    }

    // Compose the graph **in one chain** so SubFlow methods are available in-place
    val collected: Future[Seq[(String, ProcessedPartData)]] =
      source
        .via(parseRawParts)           // ByteString → RawPart
        .splitWhen(_.isLeft)          // SubFlow[RawPart, …]
        .prefixAndTail(1)             // (Seq[RawPart], Source[RawPart,_])
        .map(reconstruct)             // Part[Source[ByteString,_]] (still SubFlow)
        .concatSubstreams             // back to Flow[Part[Source[ByteString,_]], …]
        .via(
          Flow[Part[Source[ByteString, _]]]
            .mapAsync(1)(processPart) // Part[...] → (key, processedData)
            .log("processed-parts", p => s"Processed: ${p._1}")
            .withAttributes(Attributes.logLevels(onElement = Attributes.LogLevels.Debug)),
        )
        .runWith(Sink.seq)

    logger.info("Flow composition complete, executing stream")

    collected
      .map {
        parts =>
          logger.info(s"Stream execution complete: ${parts.size} parts processed")
          assembleResult(parts, detected)
      }
      .andThen {
        case scala.util.Success(r) => logger.info(s"Multipart parsing successful: ${r.parts.size} parts")
        case scala.util.Failure(e) => logger.error(s"Multipart parsing failed: ${e.getMessage}", e)
      }
  }

  /**
   * Process individual part - materialize body and extract metadata
   */
  private def processPart(
    part: Part[Source[ByteString, _]],
  )(implicit mat: Materializer,
    ec: ExecutionContext,
  ): Future[(String, ProcessedPartData)] =
    part match {
      case DataPart(key, value) =>
        logger.debug(s"Processing DataPart: key=$key, size=${value.size}")
        Future.successful(
          (
            key,
            ProcessedPartData(
              info = FormDataPartInfo(key, None, None),
              data = value.toArray,
            ),
          ),
        )

      case FilePart(key, filename, contentType, bodySource) =>
        logger.info(s"Processing FilePart: key=$key, filename=$filename")

        bodySource
          .runFold(ByteString.empty)(_ ++ _)
          .map {
            bytes =>
              logger.debug(s"FilePart materialized: key=$key, size=${bytes.size} bytes")
              (
                key,
                ProcessedPartData(
                  info = FormDataPartInfo(key, Some(filename), contentType),
                  data = bytes.toArray,
                ),
              )
          }
          .recover {
            case ex: Exception =>
              logger.error(s"Error materializing FilePart: key=$key, filename=$filename", ex)
              (
                key,
                ProcessedPartData(
                  info = FormDataPartInfo(key, Some(filename), contentType),
                  data = Array.empty,
                ),
              )
          }

      case BadPart(headers, value) =>
        val contentId = headers.getOrElse("content-id", "<unknown>")
        logger.debug(s"Processing BadPart: contentId=$contentId, size=${value.size}")

        // Try to classify using headers
        val partInfo = headers.get("content-id") match {
          case Some(cid) if !headers.contains("content-disposition") =>
            // This is likely multipart/related
            RelatedPartInfo(
              contentId = cid,
              contentType = headers.get("content-type"),
              contentLocation = headers.get("content-location"),
            )
          case _                                                     =>
            UnknownPartInfo(headers)
        }

        Future.successful(
          (
            partInfo.identifier,
            ProcessedPartData(info = partInfo, data = value.toArray),
          ),
        )

      case ParseError(message) =>
        logger.error(s"ParseError encountered: $message")
        Future.successful(
          (
            "<parseError>",
            ProcessedPartData(
              info = UnknownPartInfo(Map("error" -> message)),
              data = Array.empty,
            ),
          ),
        )

      case MaxMemoryBufferExceeded(message) =>
        logger.error(s"MaxMemoryBufferExceeded: $message")
        Future.successful(
          (
            "<bufferExceeded>",
            ProcessedPartData(
              info = UnknownPartInfo(Map("error" -> message)),
              data = Array.empty,
            ),
          ),
        )
    }

  /**
   * Assemble final MultipartResult from processed parts
   */
  private def assembleResult(
    parts: Seq[(String, ProcessedPartData)],
    detected: DetectedFormat,
  ): MultipartResult = {

    logger.info(s"Assembling result from ${parts.size} parts")

    val multipartParts = parts.map {
      case (identifier, processed) =>
        logger.trace(s"Creating MultipartPart: identifier=$identifier, size=${processed.data.length}")
        MultipartPart(
          info = processed.info,
          data = processed.data,
        )
    }

    val result = MultipartResult(
      parts = multipartParts,
      metadata = MultipartMetadata(
        format = detected.format,
        boundary = detected.boundary,
        rootPart = detected.rootPart,
        contentType = detected.contentType,
      ),
    )

    logger.info(s"Result assembled: ${result.parts.size} parts, format=${result.metadata.format.name}")
    result
  }
}

/**
 * Internal data structure for processed parts
 */
private case class ProcessedPartData(
  info: PartInfo,
  data: Array[Byte])
