package com.github.raghavaindrakj.mediapipevision.storage

import com.github.raghavaindrakj.mediapipevision.model.LearnedIdSummary

internal interface SimilarityIndex {
    suspend fun add(sampleId: String, externalId: String, displayName: String?, embedding: FloatArray)

    suspend fun removeSample(sampleId: String): Boolean

    suspend fun removeId(externalId: String): Int

    suspend fun search(query: FloatArray, k: Int): List<ScoredSample>

    suspend fun listIds(): List<LearnedIdSummary>

    suspend fun countSamples(): Int

    suspend fun countDistinctIds(): Int

    fun close()
}
