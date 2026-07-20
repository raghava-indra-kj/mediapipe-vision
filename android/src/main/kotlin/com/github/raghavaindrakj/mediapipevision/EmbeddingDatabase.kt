package com.github.raghavaindrakj.mediapipevision

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.raghavaindrakj.mediapipevision.exception.EmbeddingStoreErrorCodes
import com.github.raghavaindrakj.mediapipevision.exception.EmbeddingStoreException
import com.github.raghavaindrakj.mediapipevision.model.Match
import com.github.raghavaindrakj.mediapipevision.model.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

private const val DB_VERSION = 1
private const val TABLE_SUBJECTS = "subjects"
private const val TABLE_FEATURES = "features"
private const val COL_SUBJECT_ID = "subject_id"
private const val COL_NAME = "name"
private const val COL_FEATURE_COUNT = "feature_count"
private const val COL_CREATED_AT = "created_at"
private const val COL_FEATURE_ID = "feature_id"
private const val COL_VECTOR = "vector"

/**
 * SQLite-backed persistence and recognition for [EmbeddingStore]: exact linear-scan cosine
 * similarity, no native vector index, no native dependency at all — only plain framework SQLite.
 */
internal class EmbeddingDatabase {
    private lateinit var openHelper: EmbeddingSqliteOpenHelper
    private lateinit var db: SQLiteDatabase

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        openHelper = EmbeddingSqliteOpenHelper(context.applicationContext)
        db = openHelper.writableDatabase
    }

    fun close() {
        openHelper.close()
    }

    suspend fun createSubject(subjectId: String, name: String) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(COL_SUBJECT_ID, subjectId)
                put(COL_NAME, name)
                put(COL_FEATURE_COUNT, 0)
                put(COL_CREATED_AT, System.currentTimeMillis())
            }
            try {
                db.insertOrThrow(TABLE_SUBJECTS, null, values)
            } catch (e: SQLiteConstraintException) {
                throw EmbeddingStoreException(
                    message = "Subject already exists: $subjectId",
                    errorCode = EmbeddingStoreErrorCodes.SUBJECT_ALREADY_EXISTS,
                    cause = e,
                )
            }
        }
    }

    suspend fun updateSubject(subjectId: String, name: String) {
        withContext(Dispatchers.IO) {
            db.runInTx {
                requireSubjectRow(subjectId)
                update(
                    TABLE_SUBJECTS, ContentValues().apply { put(COL_NAME, name) },
                    "$COL_SUBJECT_ID = ?", arrayOf(subjectId)
                )
            }
        }
    }

    suspend fun deleteSubject(subjectId: String) {
        withContext(Dispatchers.IO) {
            db.runInTx {
                requireSubjectRow(subjectId)
                delete(TABLE_FEATURES, "$COL_SUBJECT_ID = ?", arrayOf(subjectId))
                delete(TABLE_SUBJECTS, "$COL_SUBJECT_ID = ?", arrayOf(subjectId))
            }
        }
    }

    suspend fun listSubjects(): List<Subject> {
        return withContext(Dispatchers.IO) {
            db.query(TABLE_SUBJECTS, null, null, null, null, null, "$COL_CREATED_AT ASC").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.toSubject()) }
            }
        }
    }

    suspend fun getSubject(subjectId: String): Subject? {
        return withContext(Dispatchers.IO) {
            db.query(TABLE_SUBJECTS, null, "$COL_SUBJECT_ID = ?", arrayOf(subjectId), null, null, null).use { cursor ->
                if (cursor.moveToFirst()) cursor.toSubject() else null
            }
        }
    }

    suspend fun countSubjects(): Int {
        return withContext(Dispatchers.IO) {
            DatabaseUtils.queryNumEntries(db, TABLE_SUBJECTS).toInt()
        }
    }

    suspend fun createFeature(subjectId: String, featureId: String, vector: FloatArray) {
        withContext(Dispatchers.IO) {
            db.runInTx {
                requireSubjectRow(subjectId)
                val values = ContentValues().apply {
                    put(COL_FEATURE_ID, featureId)
                    put(COL_SUBJECT_ID, subjectId)
                    put(COL_VECTOR, vector.toByteArray())
                    put(COL_CREATED_AT, System.currentTimeMillis())
                }
                insertOrThrow(TABLE_FEATURES, null, values)
                execSQL(
                    "UPDATE $TABLE_SUBJECTS SET $COL_FEATURE_COUNT = $COL_FEATURE_COUNT + 1 WHERE $COL_SUBJECT_ID = ?",
                    arrayOf(subjectId)
                )
            }
        }
    }

    suspend fun deleteFeature(featureId: String) {
        withContext(Dispatchers.IO) {
            db.runInTx {
                val subjectId = query(
                    TABLE_FEATURES, arrayOf(COL_SUBJECT_ID), "$COL_FEATURE_ID = ?", arrayOf(featureId),
                    null, null, null
                ).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        throw EmbeddingStoreException(
                            message = "Feature not found: $featureId",
                            errorCode = EmbeddingStoreErrorCodes.FEATURE_NOT_FOUND,
                        )
                    }
                    cursor.getString(0)
                }
                delete(TABLE_FEATURES, "$COL_FEATURE_ID = ?", arrayOf(featureId))
                execSQL(
                    "UPDATE $TABLE_SUBJECTS SET $COL_FEATURE_COUNT = $COL_FEATURE_COUNT - 1 WHERE $COL_SUBJECT_ID = ?",
                    arrayOf(subjectId)
                )
            }
        }
    }

    suspend fun listFeatures(subjectId: String): List<String> {
        return withContext(Dispatchers.IO) {
            requireSubjectRow(subjectId)
            db.query(
                TABLE_FEATURES, arrayOf(COL_FEATURE_ID), "$COL_SUBJECT_ID = ?", arrayOf(subjectId),
                null, null, null
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        }
    }

    suspend fun recognize(query: FloatArray, k: Int): List<Match> {
        val sql = """
            SELECT f.$COL_SUBJECT_ID, s.$COL_NAME, f.$COL_VECTOR
            FROM $TABLE_FEATURES f INNER JOIN $TABLE_SUBJECTS s ON s.$COL_SUBJECT_ID = f.$COL_SUBJECT_ID
        """.trimIndent()
        return withContext(Dispatchers.IO) {
            db.rawQuery(sql, null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val vector = cursor.getBlob(2).toFloatArray()
                        add(
                            Match(
                                subjectId = cursor.getString(0),
                                name = cursor.getString(1),
                                confidence = cosineSimilarity(query, vector) * 100f
                            )
                        )
                    }
                }.sortedByDescending { it.confidence }.take(k)
            }
        }
    }

    private fun requireSubjectRow(subjectId: String) {
        db.query(
            TABLE_SUBJECTS, arrayOf(COL_SUBJECT_ID), "$COL_SUBJECT_ID = ?", arrayOf(subjectId),
            null, null, null
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                throw EmbeddingStoreException(
                    message = "Subject not found: $subjectId",
                    errorCode = EmbeddingStoreErrorCodes.SUBJECT_NOT_FOUND,
                )
            }
        }
    }

    private fun Cursor.toSubject() = Subject(
        subjectId = getString(getColumnIndexOrThrow(COL_SUBJECT_ID)),
        name = getString(getColumnIndexOrThrow(COL_NAME)),
        featureCount = getInt(getColumnIndexOrThrow(COL_FEATURE_COUNT)),
        createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
    )
}

/** Runs [block] inside one SQLite transaction. */
private inline fun SQLiteDatabase.runInTx(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}

private fun FloatArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    buffer.asFloatBuffer().put(this)
    return buffer.array()
}

private fun ByteArray.toFloatArray(): FloatArray {
    val floatBuffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    return FloatArray(floatBuffer.remaining()).also { floatBuffer.get(it) }
}

/** Standard cosine similarity in [-1, 1]; `similarity * 100f` is the confidence percentage. */
private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denominator = sqrt(normA) * sqrt(normB)
    return if (denominator == 0.0) 0f else (dot / denominator).toFloat()
}

private class EmbeddingSqliteOpenHelper(context: Context) :
    SQLiteOpenHelper(context, SQLITE_DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SUBJECTS (
                $COL_SUBJECT_ID TEXT PRIMARY KEY NOT NULL,
                $COL_NAME TEXT NOT NULL,
                $COL_FEATURE_COUNT INTEGER NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_FEATURES (
                $COL_FEATURE_ID TEXT PRIMARY KEY NOT NULL,
                $COL_SUBJECT_ID TEXT NOT NULL,
                $COL_VECTOR BLOB NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY($COL_SUBJECT_ID) REFERENCES $TABLE_SUBJECTS($COL_SUBJECT_ID)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_features_subject_id ON $TABLE_FEATURES($COL_SUBJECT_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("No EmbeddingDatabase schema migration defined for $oldVersion -> $newVersion.")
    }
}
