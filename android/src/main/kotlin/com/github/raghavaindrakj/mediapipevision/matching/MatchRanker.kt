package com.github.raghavaindrakj.mediapipevision.matching

import com.github.raghavaindrakj.mediapipevision.model.Match

// Purely mechanical: aggregate raw nearest-neighbor hits into one best score per id, ranked.
// No confidence threshold or ambiguity judgment here — that decision belongs to the caller.
internal object MatchRanker {

    fun rank(scoredSamples: List<ScoredSample>): List<Match> {
        val bestScorePerId = LinkedHashMap<String, Float>()
        for (sample in scoredSamples) {
            val currentBest = bestScorePerId[sample.externalId]
            if (currentBest == null || sample.score > currentBest) {
                bestScorePerId[sample.externalId] = sample.score
            }
        }
        return bestScorePerId.entries
            .map { (externalId, score) -> Match(externalId, score) }
            .sortedByDescending { it.confidence }
    }
}
