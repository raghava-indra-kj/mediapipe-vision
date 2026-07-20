package com.github.raghavaindrakj.mediapipevision.infra.vectordb.sqlite

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** DAO for the features table. */
internal class FeatureDao(private val db: SQLiteDatabase) {

    /** Inserts a feature vector and returns the generated feature ID. */
    fun insert(subjectId: String, vector: FloatArray): String {
        val featureId = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put(FeaturesTable.COL_FEATURE_ID, featureId)
            put(FeaturesTable.COL_SUBJECT_ID, subjectId)
            put(FeaturesTable.COL_VECTOR, toBlob(vector))
        }
        val rowId = db.insert(FeaturesTable.NAME, null, values)
        // Fail on rejected insert to prevent silent data loss.
        check(rowId != -1L) { "Failed to insert feature for subject $subjectId" }
        return featureId
    }

    /** Deletes a feature by ID. Returns true if a row was removed. */
    fun delete(featureId: String): Boolean {
        val rows = db.delete(
            FeaturesTable.NAME, "${FeaturesTable.COL_FEATURE_ID} = ?", arrayOf(featureId)
        )
        return rows > 0
    }

    /** Returns the IDs of all features belonging to a subject. */
    fun findIdsBySubjectId(subjectId: String): List<String> {
        // Query all feature IDs for the given subject.
        val cursor = db.query(
            FeaturesTable.NAME,
            arrayOf(FeaturesTable.COL_FEATURE_ID),
            "${FeaturesTable.COL_SUBJECT_ID} = ?", arrayOf(subjectId),
            null, null, null
        )
        val ids = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) ids.add(it.getString(0))
        }
        return ids
    }

    /** Loads every stored vector grouped by subject ID, then by feature ID. */
    fun loadAllVectors(): Map<String, Map<String, FloatArray>> {
        // Load all rows and group by subject ID.
        val cursor = db.query(
            FeaturesTable.NAME,
            arrayOf(FeaturesTable.COL_SUBJECT_ID, FeaturesTable.COL_FEATURE_ID, FeaturesTable.COL_VECTOR),
            null, null, null, null, null
        )
        val results = mutableMapOf<String, MutableMap<String, FloatArray>>()
        cursor.use {
            val subjectIdCol = it.getColumnIndexOrThrow(FeaturesTable.COL_SUBJECT_ID)
            val featureIdCol = it.getColumnIndexOrThrow(FeaturesTable.COL_FEATURE_ID)
            val vectorCol = it.getColumnIndexOrThrow(FeaturesTable.COL_VECTOR)
            while (it.moveToNext()) {
                val subjectId = it.getString(subjectIdCol)
                val featureId = it.getString(featureIdCol)
                val vector = toFloatArray(it.getBlob(vectorCol))
                results.getOrPut(subjectId) { mutableMapOf() }[featureId] = vector
            }
        }
        return results
    }

    /** Serializes a FloatArray to a little-endian BLOB. */
    private fun toBlob(vector: FloatArray): ByteArray {
        return ByteBuffer.allocate(vector.size * 4).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            vector.forEach { putFloat(it) }
        }.array()
    }

    /** Deserializes a BLOB column back into a FloatArray. */
    private fun toFloatArray(blob: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        val result = FloatArray(blob.size / 4)
        buffer.asFloatBuffer().get(result)
        return result
    }
}
