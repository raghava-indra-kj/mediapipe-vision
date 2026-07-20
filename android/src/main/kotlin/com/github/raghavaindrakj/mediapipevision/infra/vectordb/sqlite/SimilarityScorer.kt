package com.github.raghavaindrakj.mediapipevision.infra.vectordb.sqlite

import com.github.raghavaindrakj.mediapipevision.domain.vectordb.Match
import kotlin.math.sqrt

/** Ranks subjects by cosine similarity, scoring each by its best-matching feature. */
internal object SimilarityScorer {

    /** Returns the top-k matches for a query against enrolled subject features. */
    fun topMatches(
        query: FloatArray,
        featuresBySubject: Map<String, Pair<String, List<FloatArray>>>,
        k: Int
    ): List<Match> {
        if (k <= 0) return emptyList()
        val normalizedQuery = normalize(query)
        // Score each subject by its best-matching feature.
        val scored = featuresBySubject.mapNotNull { (subjectId, pair) ->
            val (name, vectors) = pair
            if (vectors.isEmpty()) return@mapNotNull null
            val maxSim = vectors.maxOf { cosineSimilarity(normalizedQuery, normalize(it)) }
            Match(
                subjectId = subjectId,
                name = name,
                confidence = (maxSim * 100f).coerceIn(0f, 100f)
            )
        }
        // Sort descending by confidence and take top k.
        return scored.sortedByDescending { it.confidence }.take(k)
    }

    /** L2-normalizes a vector to unit length. */
    private fun normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm == 0f) return vector
        return FloatArray(vector.size) { vector[it] / norm }
    }

    /** Cosine similarity via dot product. */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
        }
        return dotProduct
    }
}
