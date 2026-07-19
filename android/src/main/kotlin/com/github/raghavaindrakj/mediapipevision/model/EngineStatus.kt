package com.github.raghavaindrakj.mediapipevision.model

data class EngineStatus(
    val totalLearnedIds: Int,
    val totalSamples: Int,
    val modelVersion: String
)
