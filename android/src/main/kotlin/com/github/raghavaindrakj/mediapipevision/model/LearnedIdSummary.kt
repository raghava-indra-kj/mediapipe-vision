package com.github.raghavaindrakj.mediapipevision.model

data class LearnedIdSummary(
    val id: String,
    val displayName: String?,
    val sampleCount: Int,
    val lastUpdatedAt: Long
)
