package com.github.raghavaindrakj.mediapipevision.model

/** Subject data model. */
data class Subject(
    /** Unique identifier for this subject. */
    val subjectId: String,
    /** Human-readable name for this subject. */
    val name: String,
    /** Number of feature vectors stored for this subject. */
    val featureCount: Int,
    /** Epoch timestamp when this subject was created. */
    val createdAt: Long
)
