package com.github.raghavaindrakj.mediapipevision.storage

internal data class ScoredSample(
    val sampleId: String,
    val externalId: String,
    val score: Float
)
