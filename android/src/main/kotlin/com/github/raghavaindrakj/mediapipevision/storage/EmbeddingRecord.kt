package com.github.raghavaindrakj.mediapipevision.storage

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.VectorDistanceType

// ObjectBox's @HnswIndex requires a compile-time constant dimension count, so this must be kept
// in sync by hand with the bundled model's real embedding_dim if the model is ever swapped.
internal const val EMBEDDING_DIMENSIONS = 1280L

@Entity
data class EmbeddingRecord(
    @Id var boxId: Long = 0,
    @Index var sampleId: String = "",
    @Index var externalId: String = "",
    var displayName: String? = null,
    @HnswIndex(dimensions = EMBEDDING_DIMENSIONS, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray = FloatArray(0),
    var createdAt: Long = 0
)
