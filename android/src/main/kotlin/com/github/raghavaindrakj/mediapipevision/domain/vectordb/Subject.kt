package com.github.raghavaindrakj.mediapipevision.domain.vectordb

data class Subject(
    val subjectId: String,
    val name: String,
    val featureCount: Int,
    val createdAt: Long
)
