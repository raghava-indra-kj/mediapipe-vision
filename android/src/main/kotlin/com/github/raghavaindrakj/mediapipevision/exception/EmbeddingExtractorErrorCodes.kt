package com.github.raghavaindrakj.mediapipevision.exception

/** Error codes for embedding extraction. */
object EmbeddingExtractorErrorCodes {
    /** Model could not be loaded or initialized. */
    const val MODEL_FAILED_TO_LOAD = "embedding-extractor/model-failed-load"

    /** Model produced an embedding with an unexpected dimensionality. */
    const val UNEXPECTED_EMBEDDING_DIMENSION = "embedding-extractor/unexpected-dimension"
}
