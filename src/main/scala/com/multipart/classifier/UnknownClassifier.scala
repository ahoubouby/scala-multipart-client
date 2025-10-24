package com.multipart.classifier

import com.multipart.client.{PartInfo, UnknownPartInfo}

/**
 * Fallback classifier that always succeeds
 */
object UnknownClassifier extends PartClassifier {

  override def classify(headers: Map[String, String]): Option[PartInfo] = {
    Some(UnknownPartInfo(headers))
  }
}
