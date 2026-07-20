package com.github.raghavaindrakj.mediapipevision.infra.vectordb.sqlite

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.Match
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.Subject
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.VectorDb
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.VectorDbErrorCodes
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.VectorDbException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** SQLite-backed [VectorDb]. Writes are serialized; reads run concurrently via WAL. */
internal class SqliteVectorDb private constructor(
    private val db: SQLiteDatabase
) : VectorDb {

    /** Guards write operations and cache reloads for atomicity. */
    private val mutex = Mutex()
    private val subjectDao = SubjectDao(db)
    private val featureDao = FeatureDao(db)

    @Volatile
    private var databaseClosed = false

    /** Cached snapshot of all features keyed by subject. Cleared on every write. */
    @Volatile
    private var cachedFeatures: Map<String, Pair<String, List<FloatArray>>>? = null

    /** Creates a new subject. */
    override suspend fun createSubject(subjectId: String, name: String): Subject = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireDatabaseOpen()
            db.tx {
                if (subjectDao.exists(subjectId)) {
                    throw VectorDbException(
                        message = "Subject already exists: $subjectId",
                        errorCode = VectorDbErrorCodes.SUBJECT_ALREADY_EXISTS
                    )
                }
                subjectDao.insert(subjectId, name, System.currentTimeMillis())
            }
        }
    }

    /** Updates an existing subject's name. */
    override suspend fun updateSubject(subjectId: String, name: String): Subject = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireDatabaseOpen()
            val subject = db.tx {
                val rows = subjectDao.update(subjectId, name)
                if (rows == 0) {
                    throw VectorDbException(
                        message = "Subject not found: $subjectId",
                        errorCode = VectorDbErrorCodes.SUBJECT_NOT_FOUND
                    )
                }
                subjectDao.findById(subjectId) ?: throw VectorDbException(
                    message = "Subject not found: $subjectId",
                    errorCode = VectorDbErrorCodes.SUBJECT_NOT_FOUND
                )
            }
            cachedFeatures = null
            subject
        }
    }

    /** Deletes a subject. Features are removed via ON DELETE CASCADE. */
    override suspend fun deleteSubject(subjectId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireDatabaseOpen()
            db.tx {
                val rows = subjectDao.delete(subjectId)
                if (rows == 0) {
                    throw VectorDbException(
                        message = "Subject not found: $subjectId",
                        errorCode = VectorDbErrorCodes.SUBJECT_NOT_FOUND
                    )
                }
            }
            cachedFeatures = null
        }
    }

    /** Returns all subjects. */
    override suspend fun listSubjects(): List<Subject> = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        subjectDao.findAll()
    }

    /** Returns a subject by ID, or null if not found. */
    override suspend fun getSubject(subjectId: String): Subject? = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        subjectDao.findById(subjectId)
    }

    /** Returns the total number of subjects. */
    override suspend fun countSubjects(): Int = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        subjectDao.count()
    }

    /** Stores a feature vector for a subject. */
    override suspend fun createFeature(subjectId: String, vector: FloatArray): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireDatabaseOpen()
            val featureId = db.tx {
                if (!subjectDao.exists(subjectId)) {
                    throw VectorDbException(
                        message = "Subject not found: $subjectId",
                        errorCode = VectorDbErrorCodes.SUBJECT_NOT_FOUND
                    )
                }
                featureDao.insert(subjectId, vector)
            }
            cachedFeatures = null
            featureId
        }
    }

    /** Deletes a feature by ID. */
    override suspend fun deleteFeature(featureId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireDatabaseOpen()
            db.tx {
                if (!featureDao.delete(featureId)) {
                    throw VectorDbException(
                        message = "Feature not found: $featureId",
                        errorCode = VectorDbErrorCodes.FEATURE_NOT_FOUND
                    )
                }
            }
            cachedFeatures = null
        }
    }

    /** Returns all feature IDs for a subject. */
    override suspend fun listFeatures(subjectId: String): List<String> = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        if (!subjectDao.exists(subjectId)) {
            throw VectorDbException(
                message = "Subject not found: $subjectId",
                errorCode = VectorDbErrorCodes.SUBJECT_NOT_FOUND
            )
        }
        featureDao.findIdsBySubjectId(subjectId)
    }

    /** Finds the top-k nearest neighbors for a query vector. */
    override suspend fun recognize(query: FloatArray, k: Int): List<Match> = withContext(Dispatchers.IO) {
        requireDatabaseOpen()
        try {
            SimilarityScorer.topMatches(query, cachedOrLoad(), k)
        } catch (e: VectorDbException) {
            throw e
        } catch (e: Exception) {
            throw VectorDbException(
                message = "Recognition failed: ${e.message}",
                errorCode = VectorDbErrorCodes.RECOGNITION_FAILED,
                cause = e
            )
        }
    }

    /** Returns the cached feature snapshot, loading it once on a miss. */
    private suspend fun cachedOrLoad(): Map<String, Pair<String, List<FloatArray>>> {
        cachedFeatures?.let { return it }
        return mutex.withLock {
            cachedFeatures ?: loadFeatures()
        }
    }

    /** Builds the in-memory snapshot of every subject's vectors. */
    private fun loadFeatures(): Map<String, Pair<String, List<FloatArray>>> {
        val allFeatures = featureDao.loadAllVectors()
        val subjects = subjectDao.findAll().associateBy { it.subjectId }
        val features = allFeatures.mapNotNull { (subjectId, vectors) ->
            val name = subjects[subjectId]?.name ?: return@mapNotNull null
            subjectId to (name to vectors.values.toList())
        }.toMap()
        cachedFeatures = features
        return features
    }

    /** Releases the database. Idempotent. */
    override fun close() {
        if (databaseClosed) return
        databaseClosed = true
        cachedFeatures = null
        db.close()
    }

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
        /** Creates an instance backed by a writable SQLite database. */
        fun create(context: Context): SqliteVectorDb {
            val helper = DatabaseHelper(context)
            return SqliteVectorDb(helper.writableDatabase)
        }
    }
}
