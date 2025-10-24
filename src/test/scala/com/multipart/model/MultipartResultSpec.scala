package com.multipart.model

import com.multipart.TestFixtures
import com.multipart.client._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit tests for MultipartResult
  */
class MultipartResultSpec extends AnyWordSpec with Matchers {

  val jsonPart: MultipartPart = MultipartPart.formData(
    name        = "metadata",
    data        = TestFixtures.jsonContent,
    contentType = Some("application/json"),
  )

  val pdfPart: MultipartPart = MultipartPart.formData(
    name        = "document",
    data        = TestFixtures.pdfContent,
    filename    = Some("label.pdf"),
    contentType = Some("application/pdf"),
  )

  val imagePart: MultipartPart = MultipartPart.formData(
    name        = "avatar",
    data        = "PNG_DATA".getBytes,
    filename    = Some("photo.png"),
    contentType = Some("image/png"),
  )

  val xmlPart: MultipartPart = MultipartPart.formData(
    name        = "config",
    data        = TestFixtures.xmlContent,
    contentType = Some("application/xml"),
  )

  val metadata: MultipartMetadata = MultipartMetadata(
    format      = FormDataFormat,
    boundary    = "boundary123",
    rootPart    = None,
    contentType = "multipart/form-data; boundary=boundary123",
  )

  "MultipartResult" should {

    "get part by identifier" in {
      val parts  = Seq(jsonPart, pdfPart)
      val result = MultipartResult(parts, metadata)

      result.getPart("metadata") shouldBe Some(jsonPart)
      result.getPart("document") shouldBe Some(pdfPart)
      result.getPart("nonexistent") shouldBe None
    }

    "get parts by type predicate" in {
      val parts  = Seq(jsonPart, pdfPart, imagePart)
      val result = MultipartResult(parts, metadata)

      val formDataParts = result.getPartsByType {
        case info: FormDataPartInfo if info.filename.isDefined => true
        case _                                                  => false
      }

      formDataParts should have size 2
      formDataParts should contain(pdfPart)
      formDataParts should contain(imagePart)
    }

    "get parts matching regex pattern" in {
      val labelPart = MultipartPart.formData(
        name = "shipping-label",
        data = "data".getBytes,
      )
      val parts  = Seq(jsonPart, labelPart, pdfPart)
      val result = MultipartResult(parts, metadata)

      val matching = result.getPartsMatching(".*label.*")

      matching should have size 1
      matching.head shouldBe labelPart
    }

    "filter JSON parts" in {
      val parts  = Seq(jsonPart, pdfPart, imagePart, xmlPart)
      val result = MultipartResult(parts, metadata)

      val jsonParts = result.jsonParts

      jsonParts should have size 1
      jsonParts.head shouldBe jsonPart
    }

    "filter PDF parts" in {
      val parts  = Seq(jsonPart, pdfPart, imagePart)
      val result = MultipartResult(parts, metadata)

      val pdfParts = result.pdfParts

      pdfParts should have size 1
      pdfParts.head shouldBe pdfPart
    }

    "filter image parts" in {
      val parts  = Seq(jsonPart, pdfPart, imagePart)
      val result = MultipartResult(parts, metadata)

      val imageParts = result.imageParts

      imageParts should have size 1
      imageParts.head shouldBe imagePart
    }

    "filter XML parts" in {
      val parts  = Seq(jsonPart, pdfPart, xmlPart)
      val result = MultipartResult(parts, metadata)

      val xmlParts = result.xmlParts

      xmlParts should have size 1
      xmlParts.head shouldBe xmlPart
    }

    "get parts by content type pattern" in {
      val parts  = Seq(jsonPart, pdfPart, imagePart)
      val result = MultipartResult(parts, metadata)

      val applicationParts = result.getPartsByContentType("application")

      applicationParts should have size 2
      applicationParts should contain(jsonPart)
      applicationParts should contain(pdfPart)
    }

    "convert to byte map" in {
      val parts  = Seq(jsonPart, pdfPart)
      val result = MultipartResult(parts, metadata)

      val byteMap = result.toByteMap

      byteMap should have size 2
      byteMap.keys should contain allOf ("metadata", "document")
      byteMap("metadata") shouldBe TestFixtures.jsonContent
      byteMap("document") shouldBe TestFixtures.pdfContent
    }

    "convert to part map" in {
      val parts  = Seq(jsonPart, pdfPart)
      val result = MultipartResult(parts, metadata)

      val partMap = result.toPartMap

      partMap should have size 2
      partMap("metadata") shouldBe jsonPart
      partMap("document") shouldBe pdfPart
    }

    "have meaningful toString" in {
      val parts  = Seq(jsonPart, pdfPart, imagePart)
      val result = MultipartResult(parts, metadata)

      val str = result.toString
      str should include("parts=3")
      // str should include("multipart/form-data")
    }

    "handle empty parts list" in {
      val result = MultipartResult(Seq.empty, metadata)

      result.parts shouldBe empty
      result.jsonParts shouldBe empty
      result.pdfParts shouldBe empty
      result.toByteMap shouldBe empty
    }
  }

  "MultipartMetadata" should {

    "store format information" in {
      metadata.format shouldBe FormDataFormat
      metadata.boundary shouldBe "boundary123"
      metadata.contentType should include("multipart/form-data")
    }

    "support optional root part" in {
      val withRoot = metadata.copy(rootPart = Some("<root>"))
      withRoot.rootPart shouldBe Some("<root>")
    }
  }

  "MultipartFormat" should {

    "have correct format names" in {
      FormDataFormat.name shouldBe "multipart/form-data"
      RelatedFormat.name shouldBe "multipart/related"
      MixedFormat.name shouldBe "multipart/mixed"
      UnknownFormat.name shouldBe "multipart/unknown"
    }

    "detect format from content-type" in {
      MultipartFormat.fromContentType("multipart/form-data; boundary=abc") shouldBe FormDataFormat
      MultipartFormat.fromContentType("multipart/related; boundary=xyz") shouldBe RelatedFormat
      MultipartFormat.fromContentType("multipart/mixed; boundary=123") shouldBe MixedFormat
      MultipartFormat.fromContentType("text/plain") shouldBe UnknownFormat
    }

    "handle case-insensitive detection" in {
      MultipartFormat.fromContentType("MULTIPART/FORM-DATA; boundary=abc") shouldBe FormDataFormat
      MultipartFormat.fromContentType("Multipart/Related; boundary=xyz") shouldBe RelatedFormat
    }
  }
}
