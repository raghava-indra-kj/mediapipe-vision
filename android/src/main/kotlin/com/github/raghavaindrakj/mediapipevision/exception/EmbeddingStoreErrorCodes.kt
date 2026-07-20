package com.github.raghavaindrakj.mediapipevision.exception

/** Error codes for store operations. */
object EmbeddingStoreErrorCodes {
    /** Operation attempted before initialization. */
    const val NOT_INITIALIZED = "embedding-store/not-initialized"
    /** Operation attempted after the store was closed. */
    const val STORE_CLOSED = "embedding-store/closed"
    /** Referenced subject does not exist. */
    const val SUBJECT_NOT_FOUND = "embedding-store/subject-not-found"
    /** Subject with the same ID already exists. */
    const val SUBJECT_ALREADY_EXISTS = "embedding-store/subject-already-exists"
    /** Referenced feature does not exist. */
    const val FEATURE_NOT_FOUND = "embedding-store/feature-not-found"
}
