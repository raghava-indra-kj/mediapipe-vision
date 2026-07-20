package com.github.raghavaindrakj.mediapipevision.model

/** Subject matched with a confidence score. */
data class Match(
    /** Unique identifier of the matched subject. */
    val subjectId: String,
    /** Human-readable name of the matched subject. */
    val name: String,
    /** Confidence percentage in [0, 100]. */
    val confidence: Float
)
