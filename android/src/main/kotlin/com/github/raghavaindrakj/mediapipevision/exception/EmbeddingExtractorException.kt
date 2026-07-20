package com.github.raghavaindrakj.mediapipevision.exception

/** Exception thrown when embedding extraction fails. */
class EmbeddingExtractorException(
    message: String,
    errorCode: String? = null,
    data: Any? = null,
    cause: Throwable? = null
) : EmbeddingException(message, errorCode, data, cause)
