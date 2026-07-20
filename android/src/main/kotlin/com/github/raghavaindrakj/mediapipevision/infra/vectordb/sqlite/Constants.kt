package com.github.raghavaindrakj.mediapipevision.infra.vectordb.sqlite

/** Database-level configuration. */
internal object Db {
    const val NAME = "vectordb.db"
    const val VERSION = 1
}

/** Schema of the subjects table. */
internal object SubjectsTable {
    const val NAME = "subjects"
    const val COL_SUBJECT_ID = "subject_id"
    const val COL_NAME = "name"
    const val COL_CREATED_AT = "created_at"
}

/** Schema of the features table. */
internal object FeaturesTable {
    const val NAME = "features"
    const val COL_FEATURE_ID = "feature_id"
    const val COL_SUBJECT_ID = "subject_id"
    const val COL_VECTOR = "vector"
    const val INDEX_SUBJECT_ID = "idx_features_subject_id"
}
