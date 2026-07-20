package com.github.raghavaindrakj.mediapipevision.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique

/** A distinct subject whose data has been enrolled. */
@Entity
internal data class SubjectEntity(
    /** ObjectBox-assigned row identifier. */
    @Id var boxId: Long = 0,
    /** Application-level unique identifier for this subject. */
    @Unique var subjectId: String = "",
    /** Human-readable name for this subject. */
    var name: String = "",
    /** Number of feature vectors stored for this subject. */
    var featureCount: Int = 0,
    /** Epoch timestamp when this subject was created. */
    var createdAt: Long = 0
)
