package com.github.raghavaindrakj.mediapipevision.storage

import android.content.Context
import com.github.raghavaindrakj.mediapipevision.model.LearnedIdSummary
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ObjectBoxSimilarityIndex private constructor(
    private val boxStore: BoxStore,
    private val box: Box<EmbeddingRecord>
) : SimilarityIndex {

    companion object {
        fun create(context: Context): ObjectBoxSimilarityIndex {
            val boxStore = MyObjectBox.builder().androidContext(context.applicationContext).build()
            return ObjectBoxSimilarityIndex(boxStore, boxStore.boxFor(EmbeddingRecord::class.java))
        }
    }

    override suspend fun add(sampleId: String, externalId: String, displayName: String?, embedding: FloatArray) {
        withContext(Dispatchers.IO) {
            box.put(
                EmbeddingRecord(
                    sampleId = sampleId,
                    externalId = externalId,
                    displayName = displayName,
                    embedding = embedding,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun removeSample(sampleId: String): Boolean = withContext(Dispatchers.IO) {
        box.query(EmbeddingRecord_.sampleId.equal(sampleId)).build().use { query ->
            val ids = query.findIds()
            if (ids.isEmpty()) return@withContext false
            box.removeByIds(ids.toMutableList())
            true
        }
    }

    override suspend fun removeId(externalId: String): Int = withContext(Dispatchers.IO) {
        box.query(EmbeddingRecord_.externalId.equal(externalId)).build().use { query ->
            val ids = query.findIds()
            box.removeByIds(ids.toMutableList())
            ids.size
        }
    }

    override suspend fun search(query: FloatArray, k: Int): List<ScoredSample> = withContext(Dispatchers.IO) {
        box.query(EmbeddingRecord_.embedding.nearestNeighbors(query, k)).build().use { builtQuery ->
            builtQuery.findWithScores().map { result ->
                val record = result.get()
                ScoredSample(
                    sampleId = record.sampleId,
                    externalId = record.externalId,
                    score = cosineDistanceToConfidence(result.score.toFloat())
                )
            }
        }
    }

    override suspend fun listIds(): List<LearnedIdSummary> = withContext(Dispatchers.IO) {
        box.all
            .groupBy { it.externalId }
            .map { (externalId, records) ->
                LearnedIdSummary(
                    id = externalId,
                    displayName = records.lastOrNull { it.displayName != null }?.displayName,
                    sampleCount = records.size,
                    lastUpdatedAt = records.maxOf { it.createdAt }
                )
            }
    }

    override suspend fun countSamples(): Int = withContext(Dispatchers.IO) { box.count().toInt() }

    override suspend fun countDistinctIds(): Int = withContext(Dispatchers.IO) {
        box.all.map { it.externalId }.distinct().size
    }

    override fun close() {
        boxStore.close()
    }

    // ObjectBox reports HNSW cosine results as a distance (0 = identical), the same convention
    // most HNSW-based vector stores use. This mirrors the confidence definition the original
    // prototype used (confidence = 1 - distance); worth confirming against a real device once
    // the instrumented tests can run.
    private fun cosineDistanceToConfidence(distance: Float): Float = 1f - distance
}
