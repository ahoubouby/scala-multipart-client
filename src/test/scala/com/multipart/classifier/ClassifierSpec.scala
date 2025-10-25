package com.multipart.classifier

import com.multipart.client._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit tests for all PartClassifier implementations
  */
class ClassifierSpec extends AnyWordSpec with Matchers {

  "FormDataClassifier" should {

    "classify form-data field with name only" in {
      val headers = Map("content-disposition" -> """form-data; name="username"""")

      val result = FormDataClassifier.classify(headers)

      result shouldBe defined
      result.get shouldBe a[FormDataPartInfo]
      val info = result.get.asInstanceOf[FormDataPartInfo]
      info.name shouldBe "username"
      info.filename shouldBe None
      info.contentType shouldBe None
    }

    "classify form-data field with name and filename" in {
      val headers = Map(
        "content-disposition" -> """form-data; name="file"; filename="document.pdf"""",
        "content-type"        -> "application/pdf",
      )

      val result = FormDataClassifier.classify(headers)

      result shouldBe defined
      val info = result.get.asInstanceOf[FormDataPartInfo]
      info.name shouldBe "file"
      info.filename shouldBe Some("document.pdf")
      info.contentType shouldBe Some("application/pdf")
    }

    "handle filename with spaces" in {
      val headers = Map(
        "content-disposition" -> """form-data; name="upload"; filename="my document.pdf"""",
      )

      val result = FormDataClassifier.classify(headers)

      result shouldBe defined
      val info = result.get.asInstanceOf[FormDataPartInfo]
      info.filename shouldBe Some("my document.pdf")
    }

    "handle quoted field names" in {
      val headers = Map(
        "content-disposition" -> """form-data; name="user-name"""",
      )

      val result = FormDataClassifier.classify(headers)

      result shouldBe defined
      result.get.asInstanceOf[FormDataPartInfo].name shouldBe "user-name"
    }

    "return None for non-form-data headers" in {
      val headers = Map(
        "content-id"   -> "<part1>",
        "content-type" -> "application/json",
      )

      val result = FormDataClassifier.classify(headers)
      result shouldBe None
    }

    "handle case-insensitive header names" in {
      val headers = Map(
        "Content-Disposition" -> """form-data; name="field1"""",
      )

      val result = FormDataClassifier.classify(headers)
      result shouldBe defined
    }
  }

  "RelatedClassifier" should {

    "classify related part with Content-ID" in {
      val headers = Map(
        "content-id"   -> "<metadata>",
        "content-type" -> "application/json",
      )

      val result = RelatedClassifier.classify(headers)

      result shouldBe defined
      result.get shouldBe a[RelatedPartInfo]
      val info = result.get.asInstanceOf[RelatedPartInfo]
      info.contentId shouldBe "<metadata>"
      info.contentType shouldBe Some("application/json")
      info.contentLocation shouldBe None
    }

    "classify related part with Content-ID and Content-Location" in {
      val headers = Map(
        "content-id"       -> "<part1>",
        "content-location" -> "/path/to/resource",
        "content-type"     -> "image/png",
      )

      val result = RelatedClassifier.classify(headers)

      result shouldBe defined
      val info = result.get.asInstanceOf[RelatedPartInfo]
      info.contentId shouldBe "<part1>"
      info.contentLocation shouldBe Some("/path/to/resource")
      info.contentType shouldBe Some("image/png")
    }

    "strip angle brackets from Content-ID" in {
      val headers = Map(
        "content-id" -> "<part123>",
      )

      val result = RelatedClassifier.classify(headers)

      result shouldBe defined
      result.get.asInstanceOf[RelatedPartInfo].contentId shouldBe "<part123>"
    }

    "return None when Content-ID is missing" in {
      val headers = Map(
        "content-type" -> "application/json",
      )

      val result = RelatedClassifier.classify(headers)
      result shouldBe None
    }

    "handle case-insensitive header names" in {
      val headers = Map(
        "Content-ID" -> "<metadata>",
      )

      val result = RelatedClassifier.classify(headers)
      result shouldBe defined
    }
  }

  "MixedClassifier" should {

    "classify mixed part with Content-ID" in {
      val headers = Map(
        "content-id"   -> "<part1>",
        "content-type" -> "text/plain",
      )

      val result = MixedClassifier.classify(headers)

      result shouldBe defined
      result.get shouldBe a[RelatedPartInfo]
      val info = result.get.asInstanceOf[RelatedPartInfo]
      info.contentId shouldBe "<part1>"
    }

    "classify mixed part with Content-Location only" in {
      val headers = Map(
        "content-location" -> "/path/to/image",
        "content-type"     -> "image/png",
      )

      val result = MixedClassifier.classify(headers)

      result shouldBe defined
      val info = result.get.asInstanceOf[RelatedPartInfo]
      info.contentId should include("/path/to/image")
      info.contentLocation shouldBe Some("/path/to/image")
    }

    "prefer Content-ID over Content-Location" in {
      val headers = Map(
        "content-id"       -> "<preferred>",
        "content-location" -> "/fallback",
      )

      val result = MixedClassifier.classify(headers)

      result shouldBe defined
      val info = result.get.asInstanceOf[RelatedPartInfo]
      info.contentId shouldBe "<preferred>"
      info.contentLocation shouldBe Some("/fallback")
    }

    "return None when neither Content-ID nor Content-Location present" in {
      val headers = Map(
        "content-type" -> "text/plain",
      )

      val result = MixedClassifier.classify(headers)
      result shouldBe None
    }
  }

  "UnknownClassifier" should {

    "always classify with UnknownPartInfo" in {
      val headers = Map(
        "x-custom-header" -> "value",
        "content-type"    -> "application/custom",
      )

      val result = UnknownClassifier.classify(headers)

      result shouldBe defined
      result.get shouldBe a[UnknownPartInfo]
      val info = result.get.asInstanceOf[UnknownPartInfo]
      info.headers shouldBe headers
    }

    "handle empty headers" in {
      val result = UnknownClassifier.classify(Map.empty)

      result shouldBe defined
      result.get.asInstanceOf[UnknownPartInfo].headers shouldBe Map.empty
    }

    "use content-id as identifier if present" in {
      val headers = Map(
        "content-id" -> "<unknown>",
      )

      val result = UnknownClassifier.classify(headers)

      result shouldBe defined
      result.get.identifier shouldBe "<unknown>"
    }

    "use <unknown> as default identifier" in {
      val result = UnknownClassifier.classify(Map.empty)

      result shouldBe defined
      result.get.identifier shouldBe "<unknown>"
    }
  }

  "ChainedClassifier" should {

    "try classifiers in order" in {
      val classifiers = Seq(
        FormDataClassifier,
        RelatedClassifier,
        UnknownClassifier,
      )
      val chained = new ChainedClassifier(classifiers)

      val formDataHeaders = Map(
        "content-disposition" -> """form-data; name="field"""",
      )

      val result = chained.classify(formDataHeaders)

      result shouldBe defined
      result.get shouldBe a[FormDataPartInfo]
    }

    "fall through to next classifier if first returns None" in {
      val classifiers = Seq(
        FormDataClassifier,
        RelatedClassifier,
        UnknownClassifier,
      )
      val chained = new ChainedClassifier(classifiers)

      val relatedHeaders = Map(
        "content-id" -> "<metadata>",
      )

      val result = chained.classify(relatedHeaders)

      result shouldBe defined
      result.get shouldBe a[RelatedPartInfo]
    }

    "use UnknownClassifier as final fallback" in {
      val classifiers = Seq(
        FormDataClassifier,
        RelatedClassifier,
        UnknownClassifier,
      )
      val chained = new ChainedClassifier(classifiers)

      val customHeaders = Map(
        "x-custom" -> "value",
      )

      val result = chained.classify(customHeaders)

      result shouldBe defined
      result.get shouldBe a[UnknownPartInfo]
    }

    "return first successful classification" in {
      // Edge case: Content-ID could match both Related and Mixed
      val classifiers = Seq(
        RelatedClassifier,
        MixedClassifier,
        UnknownClassifier,
      )
      val chained = new ChainedClassifier(classifiers)

      val headers = Map(
        "content-id" -> "<part>",
      )

      val result = chained.classify(headers)

      result shouldBe defined
      result.get shouldBe a[RelatedPartInfo]
    }
  }
}
