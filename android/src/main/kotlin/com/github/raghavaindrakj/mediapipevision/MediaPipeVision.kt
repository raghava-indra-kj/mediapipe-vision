package com.github.raghavaindrakj.mediapipevision

import android.content.Context
import android.graphics.Bitmap
import com.github.raghavaindrakj.mediapipevision.embedding.EmbeddingExtractor
import com.github.raghavaindrakj.mediapipevision.embedding.MediaPipeEmbeddingExtractor
import com.github.raghavaindrakj.mediapipevision.internal.IdGenerator
import com.github.raghavaindrakj.mediapipevision.matching.MatchRanker
import com.github.raghavaindrakj.mediapipevision.matching.SimilarityIndex
import com.github.raghavaindrakj.mediapipevision.model.EngineStatus
import com.github.raghavaindrakj.mediapipevision.model.LearnResult
import com.github.raghavaindrakj.mediapipevision.model.LearnedIdSummary
import com.github.raghavaindrakj.mediapipevision.model.Match
import com.github.raghavaindrakj.mediapipevision.model.MediaPipeVisionException
import com.github.raghavaindrakj.mediapipevision.storage.ObjectBoxSimilarityIndex

/**
 * Learns and recognizes items from photos, entirely on-device.
 *
 * Create one instance per process via [initialize] and hold onto it — it owns a MediaPipe image
 * embedder and an ObjectBox store, both released together by [close].
 */
class MediaPipeVision private constructor(
    private val embeddingExtractor: EmbeddingExtractor,
    private val similarityIndex: SimilarityIndex
) {
    companion object {
        private const val MODEL_VERSION = "mediapipe_mobilenet_v3_large_224"
        private const val SEARCH_OVERFETCH_FACTOR = 3

        /** Loads the model and opens the local store. Call once per process. */
        suspend fun initialize(context: Context): MediaPipeVision {
            val appContext = context.applicationContext
            return MediaPipeVision(
                embeddingExtractor = MediaPipeEmbeddingExtractor.create(appContext),
                similarityIndex = ObjectBoxSimilarityIndex.create(appContext)
            )
        }
    }

    /** Learns one sample image against [id]. [displayName] is optional, purely for your own bookkeeping. */
    suspend fun learn(image: Bitmap, id: String, displayName: String? = null): LearnResult {
        requireValidImage(image)
        requireValidId(id)

        val embedding = embeddingExtractor.extract(image)
        val sampleId = IdGenerator.newSampleId()
        similarityIndex.add(sampleId, id, displayName, embedding)

        val allIds = similarityIndex.listIds()
        val sampleCountForId = allIds.firstOrNull { it.id == id }?.sampleCount ?: 1

        return LearnResult(
            id = id,
            sampleId = sampleId,
            sampleCountForId = sampleCountForId,
            totalLearnedIds = allIds.size
        )
    }

    /**
     * Compares [image] against everything learned so far, ranked descending by similarity.
     * Returns an empty list if nothing has been learned yet. Confidence is a relative similarity
     * score, not a calibrated probability — deciding what counts as "confident enough" or "too
     * close to call" is left entirely to the caller.
     */
    suspend fun recognize(image: Bitmap, maxResults: Int = 5): List<Match> {
        requireValidImage(image)

        val embedding = embeddingExtractor.extract(image)
        val overfetchCount = (maxResults * SEARCH_OVERFETCH_FACTOR).coerceAtLeast(maxResults)
        val scoredSamples = similarityIndex.search(embedding, overfetchCount)
        return MatchRanker.rank(scoredSamples).take(maxResults)
    }

    /** Removes every learned sample for [id]. Returns how many samples were removed. */
    suspend fun forgetId(id: String): Int = similarityIndex.removeId(id)

    /** Removes a single learned sample by its [sampleId] (from [LearnResult.sampleId]), leaving the rest of that id untouched. */
    suspend fun forgetSample(sampleId: String): Boolean = similarityIndex.removeSample(sampleId)

    suspend fun learnedIds(): List<LearnedIdSummary> = similarityIndex.listIds()

    suspend fun status(): EngineStatus = EngineStatus(
        totalLearnedIds = similarityIndex.countDistinctIds(),
        totalSamples = similarityIndex.countSamples(),
        modelVersion = MODEL_VERSION
    )

    /** Releases the MediaPipe embedder and the ObjectBox store. Call once, when done with this instance. */
    fun close() {
        embeddingExtractor.close()
        similarityIndex.close()
    }

    private fun requireValidImage(image: Bitmap) {
        if (image.isRecycled || image.width <= 0 || image.height <= 0) {
            throw MediaPipeVisionException.InvalidImage("Image must not be recycled and must have positive dimensions")
        }
    }

    private fun requireValidId(id: String) {
        if (id.isBlank()) {
            throw MediaPipeVisionException.InvalidId("id must not be blank")
        }
    }
}
