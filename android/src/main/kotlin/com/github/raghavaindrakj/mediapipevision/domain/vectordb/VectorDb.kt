package com.github.raghavaindrakj.mediapipevision.domain.vectordb

interface VectorDb {
    suspend fun createSubject(subjectId: String, name: String): Subject

    suspend fun updateSubject(subjectId: String, name: String): Subject

    suspend fun deleteSubject(subjectId: String)

    suspend fun listSubjects(): List<Subject>

    suspend fun getSubject(subjectId: String): Subject?

    suspend fun countSubjects(): Int

    suspend fun createFeature(subjectId: String, vector: FloatArray): String

    suspend fun deleteFeature(featureId: String)

    suspend fun listFeatures(subjectId: String): List<String>

    suspend fun recognize(query: FloatArray, k: Int): List<Match>

    fun close()
}
