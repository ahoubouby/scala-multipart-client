package com.multipart.parser

import com.multipart.classifier._

case class MultipartParserConfig(
  boundary:            String,
  maxMemoryBufferSize: Int = 1024 * 1024,
  maxHeaderSize:       Int = 4096,
  classifiers: Seq[PartClassifier] = Seq(
    FormDataClassifier, // Try form-data first (most common)
    RelatedClassifier, // Then multipart/related
    MixedClassifier, // Then multipart/mixed
    UnknownClassifier, // Always succeeds as fallback
  ),
)
