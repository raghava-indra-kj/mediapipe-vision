package com.github.raghavaindrakj.mediapipevision.model

import com.github.raghavaindrakj.mediapipevision.EMBEDDING_DIMENSIONS
import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique
import io.objectbox.annotation.VectorDistanceType

/** A single feature vector. */
@Entity
internal class FeatureEntity(
    /** ObjectBox-assigned row identifier. */
    @Id var boxId: Long = 0,
    /** Unique identifier for this feature. */
    @Unique var featureId: String = "",
    /** Subject that this feature belongs to. */
    @Index var subjectId: String = "",
    /** The embedding vector. */
    @HnswIndex(dimensions = EMBEDDING_DIMENSIONS, distanceType = VectorDistanceType.COSINE)
    var vector: FloatArray = FloatArray(0),
    /** Epoch timestamp when this feature was captured. */
    var createdAt: Long = 0
)
