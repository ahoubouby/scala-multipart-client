package com.multipart.model

import com.multipart.TestFixtures
import com.multipart.client._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit tests for MultipartPart
  */
class MultipartPartSpec extends AnyWordSpec with Matchers {

  "MultipartPart" should {

    "extract identifier from FormDataPartInfo" in {
      val info = FormDataPartInfo("username", None, None)
      val part = MultipartPart(info, Array.empty)

      part.identifier shouldBe "username"
    }

    "extract identifier from RelatedPartInfo" in {
      val info = RelatedPartInfo("<metadata>", None, None)
      val part = MultipartPart(info, Array.empty)

      part.identifier shouldBe "<metadata>"
    }

    "extract identifier from UnknownPartInfo" in {
      val headers = Map("content-id" -> "<unknown>")
      val info    = UnknownPartInfo(headers)
      val part    = MultipartPart(info, Array.empty)

      part.identifier shouldBe "<unknown>"
    }

    "extract contentType from FormDataPartInfo" in {
      val info = FormDataPartInfo("file", Some("doc.pdf"), Some("application/pdf"))
      val part = MultipartPart(info, Array.empty)

      part.contentType shouldBe Some("application/pdf")
    }

    "extract contentType from RelatedPartInfo" in {
      val info = RelatedPartInfo("<part1>", Some("application/json"), None)
      val part = MultipartPart(info, Array.empty)

      part.contentType shouldBe Some("application/json")
    }

    "extract contentType from UnknownPartInfo headers" in {
      val headers = Map("content-type" -> "text/plain")
      val info    = UnknownPartInfo(headers)
      val part    = MultipartPart(info, Array.empty)

      part.contentType shouldBe Some("text/plain")
    }

    "detect JSON content by content-type" in {
      val info = FormDataPartInfo("data", None, Some("application/json"))
      val part = MultipartPart(info, TestFixtures.plainTextContent)

      part.isJson shouldBe true
    }

    "detect JSON content by magic bytes" in {
      val info = FormDataPartInfo("data", None, None)
      val part = MultipartPart(info, TestFixtures.jsonContent)

      part.isJson shouldBe true
    }

    "detect JSON array content" in {
      val info = FormDataPartInfo("data", None, None)
      val part = MultipartPart(info, TestFixtures.jsonArrayContent)

      part.isJson shouldBe true
    }

    "detect PDF content by content-type" in {
      val info = FormDataPartInfo("file", Some("doc.pdf"), Some("application/pdf"))
      val part = MultipartPart(info, Array.empty)

      part.isPdf shouldBe true
    }

    "detect PDF content by magic bytes" in {
      val info = FormDataPartInfo("file", None, None)
      val part = MultipartPart(info, TestFixtures.pdfContent)

      part.isPdf shouldBe true
    }

    "detect image content by content-type" in {
      val info = FormDataPartInfo("avatar", Some("photo.jpg"), Some("image/jpeg"))
      val part = MultipartPart(info, Array.empty)

      part.isImage shouldBe true
    }

    "detect XML content by content-type" in {
      val info = FormDataPartInfo("data", None, Some("application/xml"))
      val part = MultipartPart(info, Array.empty)

      part.isXml shouldBe true
    }

    "detect XML content by magic bytes" in {
      val info = FormDataPartInfo("data", None, None)
      val part = MultipartPart(info, TestFixtures.xmlContent)

      part.isXml shouldBe true
    }

    "convert data to string" in {
      val content = "Hello, World!".getBytes("UTF-8")
      val info    = FormDataPartInfo("message", None, None)
      val part    = MultipartPart(info, content)

      part.asString shouldBe "Hello, World!"
    }

    "report correct size" in {
      val content = "test".getBytes("UTF-8")
      val info    = FormDataPartInfo("field", None, None)
      val part    = MultipartPart(info, content)

      part.size shouldBe 4
    }

    "have meaningful toString" in {
      val info = FormDataPartInfo("username", None, Some("text/plain"))
      val part = MultipartPart(info, "john".getBytes)

      val str = part.toString
      str should include("username")
      str should include("text/plain")
      str should include("4 bytes")
    }
  }

  "MultipartPart.formData factory" should {

    "create part with name only" in {
      val part = MultipartPart.formData(
        name = "username",
        data = "john".getBytes,
      )

      part.identifier shouldBe "username"
      part.info shouldBe a[FormDataPartInfo]
      val info = part.info.asInstanceOf[FormDataPartInfo]
      info.filename shouldBe None
      info.contentType shouldBe None
    }

    "create part with filename and content-type" in {
      val part = MultipartPart.formData(
        name        = "file",
        data        = "content".getBytes,
        filename    = Some("doc.pdf"),
        contentType = Some("application/pdf"),
      )

      val info = part.info.asInstanceOf[FormDataPartInfo]
      info.name shouldBe "file"
      info.filename shouldBe Some("doc.pdf")
      info.contentType shouldBe Some("application/pdf")
    }
  }

  "MultipartPart.related factory" should {

    "create related part" in {
      val part = MultipartPart.related(
        contentId   = "<metadata>",
        data        = "{}".getBytes,
        contentType = Some("application/json"),
      )

      part.identifier shouldBe "<metadata>"
      part.info shouldBe a[RelatedPartInfo]
      val info = part.info.asInstanceOf[RelatedPartInfo]
      info.contentId shouldBe "<metadata>"
      info.contentType shouldBe Some("application/json")
      info.contentLocation shouldBe None
    }
  }
}
