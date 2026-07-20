package com.github.raghavaindrakj.mediapipevision.infra.vectordb.supabase

/** Schema of the subjects table. */
internal object SubjectsTable {
    const val NAME = "subjects"
    const val COL_SUBJECT_ID = "subject_id"
    const val COL_NAME = "name"
    const val COL_CREATED_AT = "created_at"
}

/** Column names shared by all feature tables. */
internal object FeaturesTable {
    const val COL_FEATURE_ID = "feature_id"
    const val COL_SUBJECT_ID = "subject_id"
    const val COL_VECTOR = "vector"
}

/** Schema of the `features_mediapipe` table (1280-d vectors). */
internal object FeaturesMediaPipeTable {
    const val NAME = "features_mediapipe"
}

/** Schema of the `features_gemini` table (1536-d vectors). */
internal object FeaturesGeminiTable {
    const val NAME = "features_gemini"
}

/** Remote procedure call names used by the vector store. */
internal object Rpc {
    const val MATCH_FEATURES_MEDIAPIPE = "match_features_mediapipe"
    const val MATCH_FEATURES_GEMINI = "match_features_gemini"
}

/** Postgres error codes surfaced by PostgREST and handled by the vector store. */
internal object PgErrorCodes {
    const val UNIQUE_VIOLATION = "23505"
    const val FOREIGN_KEY_VIOLATION = "23503"
}
