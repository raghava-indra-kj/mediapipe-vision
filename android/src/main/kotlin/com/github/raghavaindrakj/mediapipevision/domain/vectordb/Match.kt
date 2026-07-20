package com.github.raghavaindrakj.mediapipevision.domain.vectordb

data class Match(
    val subjectId: String,
    val name: String,
    val confidence: Float
)
