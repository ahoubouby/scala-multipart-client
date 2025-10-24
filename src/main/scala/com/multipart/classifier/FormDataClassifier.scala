package com.multipart.classifier

import com.multipart.client._

/**
 * Classifier for multipart/form-data (RFC 7578)
 */
object FormDataClassifier extends PartClassifier {
  private val ContentDispositionPattern =
    """form-data;\s*name="([^"]+)"(?:;\s*filename="([^"]+)")?""".r

  def classify(headers: Map[String, String]): Option[PartInfo] = {
    headers.get("content-disposition").flatMap {
      case ContentDispositionPattern(name, filename) =>
        Some(FormDataPartInfo(
          name = name,
          filename = Option(filename),
          contentType = headers.get("content-type")
        ))
      case _ => None
    }
  }
}


