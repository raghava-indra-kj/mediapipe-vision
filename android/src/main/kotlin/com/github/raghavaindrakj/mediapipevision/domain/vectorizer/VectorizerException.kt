package com.github.raghavaindrakj.mediapipevision.domain.vectorizer

class VectorizerException(
    message: String,
    val errorCode: String? = null,
    val data: Any? = null,
    cause: Throwable? = null
) : Exception(message, cause)
