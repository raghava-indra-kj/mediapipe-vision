package com.github.raghavaindrakj.mediapipevision.infra.vectordb.supabase

import com.github.raghavaindrakj.mediapipevision.domain.vectordb.Match
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.Subject
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.VectorDb
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.VectorDbErrorCodes
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.VectorDbException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Identifies which embedder's feature table and RPC to use. */
enum class EmbedderType {
    MEDIAPIPE,
    GEMINI
}

/** [VectorDb] backed by a Supabase Postgres project over PostgREST HTTP. */
class SupabaseVectorDb private constructor(
    private val postgrest: PostgrestClient,
    private val embedderType: EmbedderType
) : VectorDb {

    private val featuresTableName: String = when (embedderType) {
        EmbedderType.MEDIAPIPE -> FeaturesMediaPipeTable.NAME
        EmbedderType.GEMINI -> FeaturesGeminiTable.NAME
    }

    private val matchFeaturesRpc: String = when (embedderType) {
        EmbedderType.MEDIAPIPE -> Rpc.MATCH_FEATURES_MEDIAPIPE
        EmbedderType.GEMINI -> Rpc.MATCH_FEATURES_GEMINI
    }

    /** Guards against use after close(). */
    @Volatile
    private var databaseClosed = false

    /** Creates a new subject. */
    override suspend fun createSubject(subjectId: String, name: String): Subject = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        val createdAt = System.currentTimeMillis()
        // Insert row and handle constraint violations.
        try {
            postgrest.insert(SubjectsTable.NAME, subjectJson(subjectId, name, createdAt))
        } catch (e: PostgrestException) {
            if (e.pgCode == PgErrorCodes.UNIQUE_VIOLATION) {
                throw VectorDbException(
                    message = "Subject already exists: $subjectId",
                    errorCode = VectorDbErrorCodes.SUBJECT_ALREADY_EXISTS,
                    cause = e
                )
            }
            throw unexpected("Failed to create subject", e)
        }
        Subject(subjectId, name, featureCount = 0, createdAt = createdAt)
    }

    /** Updates an existing subject's name. */
    override suspend fun updateSubject(subjectId: String, name: String): Subject = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        val patch = JSONObject().put(SubjectsTable.COL_NAME, name)
        val rows = try {
            postgrest.update(SubjectsTable.NAME, mapOf(SubjectsTable.COL_SUBJECT_ID to subjectId), patch).rows
        } catch (e: PostgrestException) {
            throw unexpected("Failed to update subject", e)
        }
        if (rows.length() == 0) throw subjectNotFound(subjectId)
        rows.getJSONObject(0).toSubject(countFeatures(subjectId))
    }

    /** Deletes a subject. Features are removed via ON DELETE CASCADE. */
    override suspend fun deleteSubject(subjectId: String) = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        val deletedCount = try {
            postgrest.delete(SubjectsTable.NAME, mapOf(SubjectsTable.COL_SUBJECT_ID to subjectId)).totalCount ?: 0
        } catch (e: PostgrestException) {
            throw unexpected("Failed to delete subject", e)
        }
        if (deletedCount == 0L) throw subjectNotFound(subjectId)
    }

    /** Returns all subjects. */
    override suspend fun listSubjects(): List<Subject> = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        val subjects = postgrest.select(SubjectsTable.NAME, columns = "*").rows
        val counts = countsBySubject()
        (0 until subjects.length()).map { i ->
            val row = subjects.getJSONObject(i)
            row.toSubject(counts[row.getString(SubjectsTable.COL_SUBJECT_ID)] ?: 0)
        }
    }

    /** Returns a subject by ID, or null if not found. */
    override suspend fun getSubject(subjectId: String): Subject? = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        val rows = postgrest.select(
            SubjectsTable.NAME, columns = "*", filters = mapOf(SubjectsTable.COL_SUBJECT_ID to subjectId)
        ).rows
        if (rows.length() == 0) return@withContext null
        rows.getJSONObject(0).toSubject(countFeatures(subjectId))
    }

    /** Returns the total number of subjects. */
    override suspend fun countSubjects(): Int = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        postgrest.select(SubjectsTable.NAME, columns = SubjectsTable.COL_SUBJECT_ID, count = true, head = true)
            .totalCount?.toInt() ?: 0
    }

    /** Stores a feature vector for a subject. */
    override suspend fun createFeature(subjectId: String, vector: FloatArray): String = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        val featureId = UUID.randomUUID().toString()
        try {
            postgrest.insert(featuresTableName, featureJson(featureId, subjectId, vector))
        } catch (e: PostgrestException) {
            if (e.pgCode == PgErrorCodes.FOREIGN_KEY_VIOLATION) throw subjectNotFound(subjectId)
            throw unexpected("Failed to create feature", e)
        }
        featureId
    }

    /** Deletes a feature by ID. */
    override suspend fun deleteFeature(featureId: String) = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        val deletedCount = try {
            postgrest.delete(featuresTableName, mapOf(FeaturesTable.COL_FEATURE_ID to featureId)).totalCount ?: 0
        } catch (e: PostgrestException) {
            throw unexpected("Failed to delete feature", e)
        }
        if (deletedCount == 0L) throw featureNotFound(featureId)
    }

    /** Returns all feature IDs for a subject. */
    override suspend fun listFeatures(subjectId: String): List<String> = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        if (getSubject(subjectId) == null) throw subjectNotFound(subjectId)
        val rows = postgrest.select(
            featuresTableName, columns = FeaturesTable.COL_FEATURE_ID, filters = mapOf(FeaturesTable.COL_SUBJECT_ID to subjectId)
        ).rows
        (0 until rows.length()).map { rows.getJSONObject(it).getString(FeaturesTable.COL_FEATURE_ID) }
    }

    /** Finds the top-k nearest neighbors for a query vector via the `match_features` RPC. */
    override suspend fun recognize(query: FloatArray, k: Int): List<Match> = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        if (k <= 0) return@withContext emptyList()
        try {
            val rows = postgrest.rpc(matchFeaturesRpc, matchFeaturesParams(query, k)).rows
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                Match(
                    subjectId = row.getString(SubjectsTable.COL_SUBJECT_ID),
                    name = row.getString(SubjectsTable.COL_NAME),
                    confidence = (row.getDouble("similarity").toFloat() * 100f).coerceIn(0f, 100f)
                )
            }
        } catch (e: Exception) {
            throw VectorDbException(
                message = "Recognition failed: ${e.message}",
                errorCode = VectorDbErrorCodes.RECOGNITION_FAILED,
                cause = e
            )
        }
    }

    /** Releases the PostgREST client connections. Idempotent. */
    override fun close() {
        if (databaseClosed) return
        databaseClosed = true
        postgrest.close()
    }

    /** Feature count for a subject via an exact count without fetching rows. */
    private fun countFeatures(subjectId: String): Int {
        return postgrest.select(
            featuresTableName, columns = FeaturesTable.COL_FEATURE_ID,
            filters = mapOf(FeaturesTable.COL_SUBJECT_ID to subjectId), count = true, head = true
        ).totalCount?.toInt() ?: 0
    }

    /** Feature count for every subject in a single round trip. */
    private fun countsBySubject(): Map<String, Int> {
        val rows = postgrest.select(featuresTableName, columns = FeaturesTable.COL_SUBJECT_ID).rows
        val counts = mutableMapOf<String, Int>()
        for (i in 0 until rows.length()) {
            val subjectId = rows.getJSONObject(i).getString(FeaturesTable.COL_SUBJECT_ID)
            counts[subjectId] = (counts[subjectId] ?: 0) + 1
        }
        return counts
    }

    /** Builds the JSON row for a subject. */
    private fun subjectJson(subjectId: String, name: String, createdAt: Long): JSONObject = JSONObject()
        .put(SubjectsTable.COL_SUBJECT_ID, subjectId)
        .put(SubjectsTable.COL_NAME, name)
        .put(SubjectsTable.COL_CREATED_AT, createdAt)

    /** Builds the JSON row for a feature with a float array vector. */
    private fun featureJson(featureId: String, subjectId: String, vector: FloatArray): JSONObject = JSONObject()
        .put(FeaturesTable.COL_FEATURE_ID, featureId)
        .put(FeaturesTable.COL_SUBJECT_ID, subjectId)
        .put(FeaturesTable.COL_VECTOR, JSONArray().apply { vector.forEach { put(it.toDouble()) } })

    /** Builds the JSON parameters for the match_features RPC. */
    private fun matchFeaturesParams(query: FloatArray, k: Int): JSONObject = JSONObject()
        .put("query_vector", JSONArray().apply { query.forEach { put(it.toDouble()) } })
        .put("match_count", k)

    /** Converts a JSON row to a [Subject]. */
    private fun JSONObject.toSubject(featureCount: Int) = Subject(
        subjectId = getString(SubjectsTable.COL_SUBJECT_ID),
        name = getString(SubjectsTable.COL_NAME),
        featureCount = featureCount,
        createdAt = getLong(SubjectsTable.COL_CREATED_AT)
    )

    /** Builds a not-found exception for a subject. */
    private fun subjectNotFound(subjectId: String) = VectorDbException(
        message = "Subject not found: $subjectId",
        errorCode = VectorDbErrorCodes.SUBJECT_NOT_FOUND
    )

    /** Builds a not-found exception for a feature. */
    private fun featureNotFound(featureId: String) = VectorDbException(
        message = "Feature not found: $featureId",
        errorCode = VectorDbErrorCodes.FEATURE_NOT_FOUND
    )

    /** Wraps an unexpected PostgREST error in a [VectorDbException]. */
    private fun unexpected(message: String, cause: Throwable) = VectorDbException(
        message = "$message: ${cause.message}",
        cause = cause
    )

    /** Throws if the database has been closed. */
    private fun requireDatabaseOpen() {
        if (databaseClosed) {
            throw VectorDbException(
                message = "Database is closed",
                errorCode = VectorDbErrorCodes.CLOSED
            )
        }
    }

    companion object {
        /** Creates an instance backed by the given Supabase project URL for a specific embedder type. */
        fun create(supabaseUrl: String, supabaseKey: String, type: EmbedderType): SupabaseVectorDb {
            return SupabaseVectorDb(PostgrestClient(supabaseUrl, supabaseKey), type)
        }
    }
}
