package com.github.raghavaindrakj.mediapipevision

import android.content.Context
import android.graphics.Bitmap
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.Match
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.Subject
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.VectorDb
import com.github.raghavaindrakj.mediapipevision.domain.vectorizer.Vectorizer
import com.github.raghavaindrakj.mediapipevision.infra.vectordb.sqlite.SqliteVectorDb
import com.github.raghavaindrakj.mediapipevision.infra.vectorizer.mediapipe.MediaPipeVectorizer

/** Entry point that composes a [Vectorizer] and a [VectorDb] into a single API. */
class VisionApi private constructor(
    private val vectorizer: Vectorizer,
    private val vectorDb: VectorDb
) {
    /** Creates a new subject and returns it. */
    suspend fun createSubject(subjectId: String, name: String): Subject = vectorDb.createSubject(subjectId, name)

    /** Updates the name of an existing subject and returns it. */
    suspend fun updateSubject(subjectId: String, name: String): Subject = vectorDb.updateSubject(subjectId, name)

    /** Removes a subject and all its features. */
    suspend fun deleteSubject(subjectId: String) = vectorDb.deleteSubject(subjectId)

    /** Returns all registered subjects. */
    suspend fun listSubjects(): List<Subject> = vectorDb.listSubjects()

    /** Returns a subject by ID, or null if not found. */
    suspend fun getSubject(subjectId: String): Subject? = vectorDb.getSubject(subjectId)

    /** Returns the total number of subjects. */
    suspend fun countSubjects(): Int = vectorDb.countSubjects()

    /** Extracts a feature vector from the image and stores it for the subject. */
    suspend fun createFeature(subjectId: String, image: Bitmap): String {
        val vector = vectorizer.extract(image)
        return vectorDb.createFeature(subjectId, vector)
    }

    /** Removes a feature vector by ID. */
    suspend fun deleteFeature(featureId: String) = vectorDb.deleteFeature(featureId)

    /** Returns all feature IDs for a subject. */
    suspend fun listFeatures(subjectId: String): List<String> = vectorDb.listFeatures(subjectId)

    /** Finds the k nearest matches for the given image. */
    suspend fun recognize(query: Bitmap, k: Int): List<Match> {
        require(k > 0) { "k must be positive, was $k" }
        val vector = vectorizer.extract(query)
        return vectorDb.recognize(vector, k)
    }

    /** Releases both the vectorizer and the database. Idempotent. */
    fun close() {
        try {
            vectorDb.close()
        } finally {
            vectorizer.close()
        }
    }

    companion object {
        /** Creates a VisionApi instance with optional custom providers. */
        fun create(
            context: Context, vectorizer: Vectorizer? = null, database: VectorDb? = null
        ): VisionApi {
            val resolvedVectorizer = vectorizer ?: MediaPipeVectorizer.create(context)
            val resolvedDatabase = try {
                database ?: SqliteVectorDb.create(context)
            } catch (e: Throwable) {
                // Prevent leaking a self-created vectorizer on database failure.
                if (vectorizer == null) resolvedVectorizer.close()
                throw e
            }
            return VisionApi(resolvedVectorizer, resolvedDatabase)
        }
    }
}
