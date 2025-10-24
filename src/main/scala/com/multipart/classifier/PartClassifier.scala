package com.multipart.classifier

import com.multipart.client._

/** Strategy for classifying a multipart part based on its headers
  */
trait PartClassifier {

  /** Attempt to classify a part based on its headers
    *
    * @param headers
    *   Map of lowercase header names to values
    * @return
    *   Some(PartInfo) if this classifier recognizes the headers, None to try next classifier
    */
  def classify(headers: Map[String, String]): Option[PartInfo]
}
