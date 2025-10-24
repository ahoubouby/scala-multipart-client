package com.multipart.classifier

import com.multipart.client.{PartInfo, RelatedPartInfo}


/**
 * Classifier for multipart/mixed (RFC 2046)
 */
object MixedClassifier extends PartClassifier {
  def classify(headers: Map[String, String]): Option[PartInfo] = {
    // Can use either Content-ID or Content-Location
    val identifier = headers.get("content-id")
      .orElse(headers.get("content-location"))

    identifier.map { id =>
      RelatedPartInfo(
        contentId = id,
        contentType = headers.get("content-type"),
        contentLocation = headers.get("content-location")
      )
    }
  }
}
