package com.github.raghavaindrakj.mediapipevision.exception

/** Exception thrown when a store operation fails. */
class EmbeddingStoreException(
    message: String,
    errorCode: String? = null,
    data: Any? = null,
    cause: Throwable? = null
) : EmbeddingException(message, errorCode, data, cause)
