package com.github.raghavaindrakj.mediapipevision

import android.content.Context
import android.graphics.Bitmap
import com.github.raghavaindrakj.mediapipevision.exception.EmbeddingStoreErrorCodes
import com.github.raghavaindrakj.mediapipevision.exception.EmbeddingStoreException
import com.github.raghavaindrakj.mediapipevision.model.Match
import com.github.raghavaindrakj.mediapipevision.model.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Store for managing subjects and their enrolled data. */
class EmbeddingStore {

    /** Lifecycle states for the store. */
    private enum class State { UNINITIALIZED, READY, CLOSED }

    /** SQLite-backed persistence and recognition. */
    private lateinit var database: EmbeddingDatabase

    /** Embedding extractor for inference. */
    private lateinit var extractor: EmbeddingExtractor

    /** Guards lifecycle transitions. */
    private val lifecycleMutex = Mutex()

    /** Published only after every field has been assigned. */
    @Volatile
    private var state: State = State.UNINITIALIZED

    //#region Setup

    /** Initializes the store and loads the embedder. */
    suspend fun initialize(context: Context) = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) {
            database = EmbeddingDatabase()
            database.initialize(context)
            extractor = EmbeddingExtractor.create(context)
            state = State.READY
        }
    }

    /** Releases the store and the embedder. */
    suspend fun close() = lifecycleMutex.withLock {
        requireInitialized()
        withContext(Dispatchers.IO) {
            extractor.close()
            database.close()
            state = State.CLOSED
        }
    }

    //#endregion Setup

    //#region Subject CRUD

    /** Creates a new subject with the given ID and name. */
    suspend fun createSubject(subjectId: String, name: String) {
        requireInitialized()
        database.createSubject(subjectId, name)
    }

    /** Updates the name of an existing subject. */
    suspend fun updateSubject(subjectId: String, name: String) {
        requireInitialized()
        database.updateSubject(subjectId, name)
    }

    /** Deletes a subject and all its features. */
    suspend fun deleteSubject(subjectId: String) {
        requireInitialized()
        database.deleteSubject(subjectId)
    }

    /** Returns all subjects. */
    suspend fun listSubjects(): List<Subject> {
        requireInitialized()
        return database.listSubjects()
    }

    /** Returns a single subject by its ID, or null if not found. */
    suspend fun getSubject(subjectId: String): Subject? {
        requireInitialized()
        return database.getSubject(subjectId)
    }

    /** Returns the total number of subjects. */
    suspend fun countSubjects(): Int {
        requireInitialized()
        return database.countSubjects()
    }

    //#endregion Subject CRUD

    //#region Feature CRUD

    /** Extracts an embedding from [image] and stores it under [subjectId]. */
    suspend fun createFeature(subjectId: String, image: Bitmap) {
        requireInitialized()
        val featureId = UUID.randomUUID().toString()
        val vector = extractor.extract(image)
        database.createFeature(subjectId, featureId, vector)
    }

    /** Deletes a feature and decrements its subject's feature count. */
    suspend fun deleteFeature(featureId: String) {
        requireInitialized()
        database.deleteFeature(featureId)
    }

    /** Returns all feature IDs for the given subject. */
    suspend fun listFeatures(subjectId: String): List<String> {
        requireInitialized()
        return database.listFeatures(subjectId)
    }

    //#endregion Feature CRUD

    //#region Recognition

    /** Matches [image] against enrolled subjects and returns the top *k* results. */
    suspend fun recognize(image: Bitmap, k: Int): List<Match> {
        requireInitialized()
        val query = extractor.extract(image)
        return database.recognize(query, k)
    }

    //#endregion Recognition

    //#region Helpers

    private fun requireInitialized() {
        when (state) {
            State.READY -> return
            State.UNINITIALIZED -> throw EmbeddingStoreException(
                message = "EmbeddingStore has not been initialized. Call initialize(context) first.",
                errorCode = EmbeddingStoreErrorCodes.NOT_INITIALIZED,
            )

            State.CLOSED -> throw EmbeddingStoreException(
                message = "EmbeddingStore has been closed.",
                errorCode = EmbeddingStoreErrorCodes.STORE_CLOSED,
            )
        }
    }

    //#endregion Helpers
}
