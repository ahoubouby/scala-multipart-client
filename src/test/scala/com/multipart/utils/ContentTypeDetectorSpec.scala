package com.multipart.utils

import com.multipart.TestFixtures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit tests for ContentTypeDetector
  */
class ContentTypeDetectorSpec extends AnyWordSpec with Matchers {

  "ContentTypeDetector.isJson" should {

    "detect JSON from content-type header" in {
      ContentTypeDetector.isJson(Some("application/json"), Array.empty) shouldBe true
      ContentTypeDetector.isJson(Some("application/json; charset=utf-8"), Array.empty) shouldBe true
      ContentTypeDetector.isJson(Some("text/json"), Array.empty) shouldBe true
    }

    "detect JSON from content starting with {" in {
      ContentTypeDetector.isJson(None, TestFixtures.jsonContent) shouldBe true
    }

    "detect JSON from content starting with [" in {
      ContentTypeDetector.isJson(None, TestFixtures.jsonArrayContent) shouldBe true
    }

    "not detect JSON from plain text" in {
      ContentTypeDetector.isJson(None, TestFixtures.plainTextContent) shouldBe false
    }

    "not detect JSON from PDF" in {
      ContentTypeDetector.isJson(Some("application/pdf"), TestFixtures.pdfContent) shouldBe false
    }

    "handle empty data" in {
      ContentTypeDetector.isJson(None, Array.empty) shouldBe false
    }
  }

  "ContentTypeDetector.isPdf" should {

    "detect PDF from content-type header" in {
      ContentTypeDetector.isPdf(Some("application/pdf"), Array.empty) shouldBe true
    }

    "detect PDF from magic bytes %PDF" in {
      ContentTypeDetector.isPdf(None, TestFixtures.pdfContent) shouldBe true
    }

    "not detect PDF from JSON" in {
      ContentTypeDetector.isPdf(None, TestFixtures.jsonContent) shouldBe false
    }

    "not detect PDF from short content" in {
      ContentTypeDetector.isPdf(None, "PDF".getBytes) shouldBe false
    }

    "handle empty data" in {
      ContentTypeDetector.isPdf(None, Array.empty) shouldBe false
    }
  }

  "ContentTypeDetector.isImage" should {

    "detect image from content-type" in {
      ContentTypeDetector.isImage(Some("image/png")) shouldBe true
      ContentTypeDetector.isImage(Some("image/jpeg")) shouldBe true
      ContentTypeDetector.isImage(Some("image/gif")) shouldBe true
      ContentTypeDetector.isImage(Some("image/svg+xml")) shouldBe true
    }

    "not detect non-image types" in {
      ContentTypeDetector.isImage(Some("application/json")) shouldBe false
      ContentTypeDetector.isImage(Some("text/plain")) shouldBe false
    }

    "handle None content-type" in {
      ContentTypeDetector.isImage(None) shouldBe false
    }
  }

  "ContentTypeDetector.isXml" should {

    "detect XML from content-type header" in {
      ContentTypeDetector.isXml(Some("application/xml"), Array.empty) shouldBe true
      ContentTypeDetector.isXml(Some("text/xml"), Array.empty) shouldBe true
      ContentTypeDetector.isXml(Some("application/soap+xml"), Array.empty) shouldBe true
    }

    "detect XML from <?xml declaration" in {
      ContentTypeDetector.isXml(None, TestFixtures.xmlContent) shouldBe true
    }

    "detect XML from content starting with <" in {
      val xmlContent = "<root><element/></root>".getBytes
      ContentTypeDetector.isXml(None, xmlContent) shouldBe true
    }

    "not detect XML from JSON" in {
      ContentTypeDetector.isXml(None, TestFixtures.jsonContent) shouldBe false
    }

    "handle empty data" in {
      ContentTypeDetector.isXml(None, Array.empty) shouldBe false
    }
  }

  "ContentTypeDetector.detectType" should {

    "detect json type" in {
      ContentTypeDetector.detectType(Some("application/json"), Array.empty) shouldBe "json"
      ContentTypeDetector.detectType(None, TestFixtures.jsonContent) shouldBe "json"
    }

    "detect pdf type" in {
      ContentTypeDetector.detectType(Some("application/pdf"), Array.empty) shouldBe "pdf"
      ContentTypeDetector.detectType(None, TestFixtures.pdfContent) shouldBe "pdf"
    }

    "detect xml type" in {
      ContentTypeDetector.detectType(Some("application/xml"), Array.empty) shouldBe "xml"
      ContentTypeDetector.detectType(None, TestFixtures.xmlContent) shouldBe "xml"
    }

    "detect image type" in {
      ContentTypeDetector.detectType(Some("image/png"), Array.empty) shouldBe "image"
    }

    "return content-type for unknown types" in {
      ContentTypeDetector.detectType(Some("application/custom"), Array.empty) shouldBe "application/custom"
    }

    "return unknown for unrecognized content" in {
      ContentTypeDetector.detectType(None, TestFixtures.plainTextContent) shouldBe "unknown"
    }
  }

  "ContentTypeDetector.extractMimeType" should {

    "extract MIME type without parameters" in {
      ContentTypeDetector.extractMimeType("application/json") shouldBe "application/json"
    }

    "extract MIME type with charset parameter" in {
      ContentTypeDetector.extractMimeType("text/html; charset=utf-8") shouldBe "text/html"
    }

    "extract MIME type with multiple parameters" in {
      ContentTypeDetector.extractMimeType("multipart/form-data; boundary=abc123; charset=utf-8") shouldBe
        "multipart/form-data"
    }

    "trim whitespace" in {
      ContentTypeDetector.extractMimeType("  application/json  ") shouldBe "application/json"
      ContentTypeDetector.extractMimeType("text/plain ; charset=utf-8") shouldBe "text/plain"
    }

    "handle content-type without semicolon" in {
      ContentTypeDetector.extractMimeType("image/png") shouldBe "image/png"
    }
  }

  "ContentTypeDetector.matches" should {

    "match when pattern is contained in content-type" in {
      ContentTypeDetector.matches(Some("application/json"), "json") shouldBe true
      ContentTypeDetector.matches(Some("image/png"), "image") shouldBe true
      ContentTypeDetector.matches(Some("application/pdf"), "pdf") shouldBe true
    }

    "not match when pattern is not contained" in {
      ContentTypeDetector.matches(Some("application/json"), "xml") shouldBe false
      ContentTypeDetector.matches(Some("text/plain"), "image") shouldBe false
    }

    "handle None content-type" in {
      ContentTypeDetector.matches(None, "json") shouldBe false
    }

    "be case-sensitive" in {
      ContentTypeDetector.matches(Some("APPLICATION/JSON"), "json") shouldBe true
      ContentTypeDetector.matches(Some("application/json"), "JSON") shouldBe true
    }
  }
}
