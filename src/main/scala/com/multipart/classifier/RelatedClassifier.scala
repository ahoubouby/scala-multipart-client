package com.multipart.classifier

import com.multipart.client.{PartInfo, RelatedPartInfo}

/** Classifier for multipart/related (RFC 2387)
  */
object RelatedClassifier extends PartClassifier {
  def classify(headers: Map[String, String]): Option[PartInfo] =
    // Must have Content-ID and NOT have Content-Disposition
    headers.get("content-id") match {
      case Some(contentId) if !headers.contains("content-disposition") =>
        Some(
          RelatedPartInfo(
            contentId       = contentId,
            contentType     = headers.get("content-type"),
            contentLocation = headers.get("content-location"),
          ),
        )
      case _ => None
    }
}
