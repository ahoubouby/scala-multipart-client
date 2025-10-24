package com.multipart.parser

import com.multipart.TestFixtures
import com.multipart.client.HttpResponse
import com.multipart.model._
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit tests for FormatDetector
  */
class FormatDetectorSpec extends AnyWordSpec with Matchers {

  /** Mock HTTP response for testing
    */
  class MockHttpResponse(
    val statusCode:    Int,
    val responseHeaders: Map[String, Seq[String]],
  ) extends HttpResponse {
    override def status: Int                              = statusCode
    override def headers: Map[String, Seq[String]]        = responseHeaders
    override def bodyAsSource: Source[ByteString, _]      = Source.empty
  }

  "FormatDetector.detect" should {

    "detect multipart/form-data format" in {
      val response = new MockHttpResponse(
        statusCode = 200,
        responseHeaders = Map(
          "content-type" -> Seq(TestFixtures.formDataContentType("boundary123")),
        ),
      )

      val detected = FormatDetector.detect(response)

      detected.format shouldBe FormDataFormat
      detected.boundary shouldBe "boundary123"
      detected.rootPart shouldBe None
      detected.contentType should include("multipart/form-data")
    }

    "detect multipart/related format" in {
      val response = new MockHttpResponse(
        statusCode = 200,
        responseHeaders = Map(
          "content-type" -> Seq(TestFixtures.relatedContentType("abc456")),
        ),
      )

      val detected = FormatDetector.detect(response)

      detected.format shouldBe RelatedFormat
      detected.boundary shouldBe "abc456"
    }

    "detect multipart/mixed format" in {
      val response = new MockHttpResponse(
        statusCode = 200,
        responseHeaders = Map(
          "content-type" -> Seq(TestFixtures.mixedContentType("mixed-boundary")),
        ),
      )

      val detected = FormatDetector.detect(response)

      detected.format shouldBe MixedFormat
      detected.boundary shouldBe "mixed-boundary"
    }

    "extract boundary from quoted string" in {
      val response = new MockHttpResponse(
        statusCode = 200,
        responseHeaders = Map(
          "content-type" -> Seq("""multipart/form-data; boundary="quoted-boundary""""),
        ),
      )

      val detected = FormatDetector.detect(response)

      detected.boundary shouldBe "quoted-boundary"
    }

    "extract boundary without quotes" in {
      val response = new MockHttpResponse(
        statusCode = 200,
        responseHeaders = Map(
          "content-type" -> Seq("multipart/form-data; boundary=simple-boundary"),
        ),
      )

      val detected = FormatDetector.detect(response)

      detected.boundary shouldBe "simple-boundary"
    }

    "extract start parameter for multipart/related" in {
      val response = new MockHttpResponse(
        statusCode = 200,
        responseHeaders = Map(
          "content-type" -> Seq("""multipart/related; boundary="abc"; start="<root>""""),
        ),
      )

      val detected = FormatDetector.detect(response)

      detected.rootPart shouldBe Some("<root>")
    }

    "handle missing Content-Type header" in {
      val response = new MockHttpResponse(
        statusCode = 200,
        responseHeaders = Map.empty,
      )

      assertThrows[Exception] {
        FormatDetector.detect(response)
      }
    }

    "handle missing boundary parameter" in {
      val response = new MockHttpResponse(
        statusCode = 200,
        responseHeaders = Map(
          "content-type" -> Seq("multipart/form-data"),
        ),
      )

      assertThrows[Exception] {
        FormatDetector.detect(response)
      }
    }

    "handle non-multipart content-type" in {
      val response = new MockHttpResponse(
        statusCode = 200,
        responseHeaders = Map(
          "content-type" -> Seq("application/json"),
        ),
      )

      assertThrows[Exception] {
        FormatDetector.detect(response)
      }
    }

    "be case-insensitive for header names" in {
      val response = new MockHttpResponse(
        statusCode = 200,
        responseHeaders = Map(
          "Content-Type" -> Seq("multipart/form-data; boundary=test"),
        ),
      )

      val detected = FormatDetector.detect(response)

      detected.boundary shouldBe "test"
    }

    "handle complex WebKit boundary" in {
      val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
      val response = new MockHttpResponse(
        statusCode = 200,
        responseHeaders = Map(
          "content-type" -> Seq(s"multipart/form-data; boundary=$boundary"),
        ),
      )

      val detected = FormatDetector.detect(response)

      detected.boundary shouldBe boundary
    }
  }

  "DetectedFormat.toParserConfig" should {

    "create config with correct boundary" in {
      val detected = DetectedFormat(
        format      = FormDataFormat,
        boundary    = "test-boundary",
        rootPart    = None,
        contentType = "multipart/form-data; boundary=test-boundary",
      )

      val config = detected.toParserConfig()

      config.boundary shouldBe "test-boundary"
    }

    "use default memory and header sizes" in {
      val detected = DetectedFormat(
        format      = FormDataFormat,
        boundary    = "test",
        rootPart    = None,
        contentType = "multipart/form-data; boundary=test",
      )

      val config = detected.toParserConfig()

      config.maxMemoryBufferSize shouldBe 16 * 1024
      config.maxHeaderSize shouldBe 4 * 1024
    }

    "allow custom memory and header sizes" in {
      val detected = DetectedFormat(
        format      = FormDataFormat,
        boundary    = "test",
        rootPart    = None,
        contentType = "multipart/form-data; boundary=test",
      )

      val config = detected.toParserConfig(
        maxMemoryBufferSize = 32 * 1024,
        maxHeaderSize       = 8 * 1024,
      )

      config.maxMemoryBufferSize shouldBe 32 * 1024
      config.maxHeaderSize shouldBe 8 * 1024
    }

    "select FormDataClassifier for form-data format" in {
      val detected = DetectedFormat(
        format      = FormDataFormat,
        boundary    = "test",
        rootPart    = None,
        contentType = "multipart/form-data; boundary=test",
      )

      val config = detected.toParserConfig()

      config.classifiers should not be empty
      config.classifiers.head.getClass.getSimpleName shouldBe "FormDataClassifier$"
    }

    "select RelatedClassifier for related format" in {
      val detected = DetectedFormat(
        format      = RelatedFormat,
        boundary    = "test",
        rootPart    = None,
        contentType = "multipart/related; boundary=test",
      )

      val config = detected.toParserConfig()

      config.classifiers should not be empty
      config.classifiers.head.getClass.getSimpleName shouldBe "RelatedClassifier$"
    }

    "select MixedClassifier for mixed format" in {
      val detected = DetectedFormat(
        format      = MixedFormat,
        boundary    = "test",
        rootPart    = None,
        contentType = "multipart/mixed; boundary=test",
      )

      val config = detected.toParserConfig()

      config.classifiers should not be empty
      config.classifiers.head.getClass.getSimpleName shouldBe "MixedClassifier$"
    }

    "include UnknownClassifier as fallback" in {
      val detected = DetectedFormat(
        format      = FormDataFormat,
        boundary    = "test",
        rootPart    = None,
        contentType = "multipart/form-data; boundary=test",
      )

      val config = detected.toParserConfig()

      config.classifiers.last.getClass.getSimpleName shouldBe "UnknownClassifier$"
    }
  }
}
