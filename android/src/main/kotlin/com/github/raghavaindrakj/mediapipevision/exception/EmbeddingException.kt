package com.github.raghavaindrakj.mediapipevision.exception

/** Base exception for all library errors. */
open class EmbeddingException(
    /** Human-readable error description. */
    message: String,
    /** Machine-readable error code for programmatic handling. */
    val errorCode: String? = null,
    /** Optional payload with additional error context. */
    val data: Any? = null,
    /** Originating exception that caused this error. */
    cause: Throwable? = null
) : Exception(message, cause)
