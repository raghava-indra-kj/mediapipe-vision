package com.github.raghavaindrakj.mediapipevision.model

sealed class MpvRecognitionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidImage(message: String) : MpvRecognitionException(message)
    class InvalidId(message: String) : MpvRecognitionException(message)
    class ModelLoadFailure(cause: Throwable) :
        MpvRecognitionException("Failed to load embedding model: ${cause.message}", cause)
}
