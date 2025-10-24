package com.multipart.classifier

import com.multipart.client.PartInfo

/** Chain of responsibility: try classifiers in order
  */
class ChainedClassifier(classifiers: Seq[PartClassifier]) extends PartClassifier {

  override def classify(headers: Map[String, String]): Option[PartInfo] =
    classifiers.view.flatMap(_.classify(headers)).headOption
}

object ChainedClassifier {

  /** Default classifier chain for most use cases
    */
  def default: ChainedClassifier = new ChainedClassifier(
    Seq(
      FormDataClassifier,
      RelatedClassifier,
      MixedClassifier,
      UnknownClassifier,
    ),
  )

  /** Classifier chain optimized for multipart/related
    */
  def forRelated: ChainedClassifier = new ChainedClassifier(
    Seq(
      RelatedClassifier,
      FormDataClassifier,
      MixedClassifier,
      UnknownClassifier,
    ),
  )

  /** Classifier chain optimized for multipart/form-data
    */
  def forFormData: ChainedClassifier = new ChainedClassifier(
    Seq(
      FormDataClassifier,
      RelatedClassifier,
      MixedClassifier,
      UnknownClassifier,
    ),
  )
}
