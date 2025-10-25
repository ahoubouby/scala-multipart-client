package com.multipart.parser

import com.multipart.TestFixtures
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.ExecutionContext

/** Tests for handling incomplete or truncated multipart data
  */
class IncompleteMultipartSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val system: ActorSystem        = ActorSystem("incomplete-multipart-spec")
  implicit val mat: Materializer          = Materializer(system)
  implicit val ec: ExecutionContext       = system.dispatcher
  implicit val patience: PatienceConfig   = PatienceConfig(Span(5, Seconds), Span(100, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  "GenericBodyPartParser" should {

    "handle empty stream gracefully" in {
      val boundary = TestFixtures.simpleBoundary
      val config = MultipartParserConfig(
        boundary            = boundary,
        maxMemoryBufferSize = 8192,
        maxHeaderSize       = 2048,
      )

      val emptySource = Source.empty[ByteString]

      val result = emptySource
        .via(new GenericBodyPartParser(config))
        .runFold(Seq.empty[Part.RawPart])(_ :+ _)

      whenReady(result) { parts =>
        // Empty stream should complete without crashing
        parts shouldBe empty
      }
    }

    "handle stream that ends before first boundary" in {
      val boundary = TestFixtures.simpleBoundary
      val config = MultipartParserConfig(
        boundary            = boundary,
        maxMemoryBufferSize = 8192,
        maxHeaderSize       = 2048,
      )

      // Incomplete data - just a partial boundary
      val incompleteData = ByteString("--")

      val source = Source.single(incompleteData)

      val result = source
        .via(new GenericBodyPartParser(config))
        .runFold(Seq.empty[Part.RawPart])(_ :+ _)

      whenReady(result) { parts =>
        // Should complete without throwing IllegalArgumentException
        // May have a ParseError or be empty
        parts.foreach {
          case Left(err: ParseError) =>
            err.message should include("Incomplete multipart data")
          case _ => // OK
        }
      }
    }

    "handle stream that ends mid-headers" in {
      val boundary = "boundary123"
      val config = MultipartParserConfig(
        boundary            = boundary,
        maxMemoryBufferSize = 8192,
        maxHeaderSize       = 2048,
      )

      // Incomplete data - initial boundary + partial headers
      val incompleteData = ByteString(
        s"""------$boundary\r
           |Content-ID: <metadata>\r
           |Content-Type: appli""".stripMargin, // Cut off mid-header
      )

      val source = Source.single(incompleteData)

      val result = source
        .via(new GenericBodyPartParser(config))
        .runFold(Seq.empty[Part.RawPart])(_ :+ _)

      whenReady(result) { parts =>
        // Should not throw IllegalArgumentException
        // Should either complete or emit a ParseError
        parts.foreach {
          case Left(err: ParseError) =>
            err.message should include("Incomplete multipart data")
          case _ => // OK
        }
      }
    }

    "handle stream that ends mid-body" in {
      val boundary = "boundary123"
      val config = MultipartParserConfig(
        boundary            = boundary,
        maxMemoryBufferSize = 8192,
        maxHeaderSize       = 2048,
      )

      // Complete headers but incomplete body
      val incompleteData = ByteString(
        s"""------$boundary\r
           |Content-ID: <metadata>\r
           |Content-Type: application/json\r
           |\r
           |{"id": "12345", "data": "incomplete...""".stripMargin, // No closing boundary
      )

      val source = Source.single(incompleteData)

      val result = source
        .via(new GenericBodyPartParser(config))
        .runFold(Seq.empty[Part.RawPart])(_ :+ _)

      whenReady(result) { parts =>
        // Should not throw IllegalArgumentException
        // Should parse the part but may have a warning about incomplete data
        parts.size should be >= 1
      }
    }

    "handle HTTP 403 error scenario (multipart headers but empty body)" in {
      val boundary = "uuid:0619807b-1f9d-41c6-9ecc-a22d719fa42a"
      val config = MultipartParserConfig(
        boundary            = boundary,
        maxMemoryBufferSize = 16384,
        maxHeaderSize       = 4096,
      )

      // Simulate HTTP 403: server claims multipart in headers but sends empty/minimal body
      val minimalOrEmptyData = ByteString.empty // Or just a few bytes

      val source = Source.single(minimalOrEmptyData)

      val result = source
        .via(new GenericBodyPartParser(config))
        .runFold(Seq.empty[Part.RawPart])(_ :+ _)

      whenReady(result) { parts =>
        // The critical test: this should NOT throw IllegalArgumentException
        // It should complete gracefully, even if parts are empty or contain errors
        parts.foreach {
          case Left(err: ParseError) =>
            // Acceptable - parser detected incomplete data
            err.message should not be empty
          case Right(_) =>
            // Also acceptable - might have some data
            succeed
          case _ =>
            succeed
        }
      }
    }

    "recover from incomplete data without crashing the stream" in {
      val boundary = TestFixtures.simpleBoundary
      val config = MultipartParserConfig(
        boundary            = boundary,
        maxMemoryBufferSize = 8192,
        maxHeaderSize       = 2048,
      )

      // Send multiple chunks, last one incomplete
      val chunk1 = ByteString(TestFixtures.simpleFormData())
      val chunk2 = ByteString("------" + boundary + "\r\nContent-Disposition: incomplete")

      val source = Source(List(chunk1, chunk2))

      val result = source
        .via(new GenericBodyPartParser(config))
        .runFold(Seq.empty[Part.RawPart])(_ :+ _)

      whenReady(result) { parts =>
        // Should parse the complete data from chunk1
        // May or may not emit error for chunk2, but should not crash
        parts should not be empty
        val dataParts = parts.collect { case Left(p: DataPart) => p }
        dataParts.size should be >= 2 // At least the two fields from simpleFormData
      }
    }
  }
}
