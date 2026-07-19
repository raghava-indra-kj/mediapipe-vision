package com.github.raghavaindrakj.mediapipevision.model

sealed class MediaPipeVisionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidImage(message: String) : MediaPipeVisionException(message)
    class InvalidId(message: String) : MediaPipeVisionException(message)
    class ModelLoadFailure(cause: Throwable) :
        MediaPipeVisionException("Failed to load embedding model: ${cause.message}", cause)
}
