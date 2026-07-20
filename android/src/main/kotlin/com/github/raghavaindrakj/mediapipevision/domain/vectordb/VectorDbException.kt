package com.github.raghavaindrakj.mediapipevision.domain.vectordb

class VectorDbException(
    message: String,
    val errorCode: String? = null,
    val data: Any? = null,
    cause: Throwable? = null
) : Exception(message, cause)
