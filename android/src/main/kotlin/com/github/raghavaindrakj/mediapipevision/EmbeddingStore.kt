package com.github.raghavaindrakj.mediapipevision

import android.content.Context
import android.graphics.Bitmap
import com.github.raghavaindrakj.mediapipevision.exception.EmbeddingStoreErrorCodes
import com.github.raghavaindrakj.mediapipevision.exception.EmbeddingStoreException
import com.github.raghavaindrakj.mediapipevision.model.FeatureEntity
import com.github.raghavaindrakj.mediapipevision.model.FeatureEntity_
import com.github.raghavaindrakj.mediapipevision.model.Match
import com.github.raghavaindrakj.mediapipevision.model.MyObjectBox
import com.github.raghavaindrakj.mediapipevision.model.Subject
import com.github.raghavaindrakj.mediapipevision.model.SubjectEntity
import com.github.raghavaindrakj.mediapipevision.model.SubjectEntity_
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.exception.UniqueViolationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Store for managing subjects and their enrolled data. */
class EmbeddingStore {

    /** Lifecycle states for the store. */
    private enum class State { UNINITIALIZED, READY, CLOSED }

    /** ObjectBox store instance. */
    private lateinit var boxStore: BoxStore

    /** ObjectBox box for subject entities. */
    private lateinit var subjectBox: Box<SubjectEntity>

    /** ObjectBox box for feature entities. */
    private lateinit var featureBox: Box<FeatureEntity>

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
            boxStore = MyObjectBox.builder().androidContext(context.applicationContext).build()
            subjectBox = boxStore.boxFor(SubjectEntity::class.java)
            featureBox = boxStore.boxFor(FeatureEntity::class.java)
            extractor = EmbeddingExtractor.create(context)
            state = State.READY
        }
    }

    /** Releases the store and the embedder. */
    suspend fun close() = lifecycleMutex.withLock {
        requireInitialized()
        withContext(Dispatchers.IO) {
            extractor.close()
            boxStore.close()
            state = State.CLOSED
        }
    }

    //#endregion Setup

    //#region Subject CRUD

    /** Creates a new subject with the given ID and name. */
    suspend fun createSubject(subjectId: String, name: String) {
        requireInitialized()
        withContext(Dispatchers.IO) {
            try {
                subjectBox.put(
                    SubjectEntity(
                        subjectId = subjectId, name = name, featureCount = 0, createdAt = System.currentTimeMillis()
                    )
                )
            } catch (e: UniqueViolationException) {
                throw EmbeddingStoreException(
                    message = "Subject already exists: $subjectId",
                    errorCode = EmbeddingStoreErrorCodes.SUBJECT_ALREADY_EXISTS,
                    cause = e,
                )
            }
        }
    }

    /** Updates the name of an existing subject. */
    suspend fun updateSubject(subjectId: String, name: String) {
        requireInitialized()
        withContext(Dispatchers.IO) {
            boxStore.runInTx {
                val subject = requireSubjectExists(subjectId)
                subject.name = name
                subjectBox.put(subject)
            }
        }
    }

    /** Deletes a subject and all its features. */
    suspend fun deleteSubject(subjectId: String) {
        requireInitialized()
        withContext(Dispatchers.IO) {
            boxStore.runInTx {
                val subject = requireSubjectExists(subjectId)
                // Remove all features for this subject
                featureBox.query(FeatureEntity_.subjectId.equal(subjectId)).build().use { query ->
                    featureBox.removeByIds(query.findIds().toMutableList())
                }
                subjectBox.remove(subject)
            }
        }
    }

    /** Returns all subjects. */
    suspend fun listSubjects(): List<Subject> {
        requireInitialized()
        return withContext(Dispatchers.IO) {
            subjectBox.all.map { mapToSubject(it) }
        }
    }

    /** Returns a single subject by its ID, or null if not found. */
    suspend fun getSubject(subjectId: String): Subject? {
        requireInitialized()
        return withContext(Dispatchers.IO) {
            subjectBox.query(SubjectEntity_.subjectId.equal(subjectId)).build().use { query ->
                val entity = query.findFirst() ?: return@withContext null
                mapToSubject(entity)
            }
        }
    }

    /** Returns the total number of subjects. */
    suspend fun countSubjects(): Int {
        requireInitialized()
        return withContext(Dispatchers.IO) {
            subjectBox.count().toInt()
        }
    }

    //#endregion Subject CRUD

    //#region Feature CRUD

    /** Extracts an embedding from [image] and stores it under [subjectId]. */
    suspend fun createFeature(subjectId: String, image: Bitmap) {
        requireInitialized()
        val featureId = UUID.randomUUID().toString()
        // Extract embedding from image
        val vector = extractor.extract(image)
        withContext(Dispatchers.IO) {
            boxStore.runInTx {
                val subject = requireSubjectExists(subjectId)
                featureBox.put(
                    FeatureEntity(
                        featureId = featureId,
                        subjectId = subjectId,
                        vector = vector,
                        createdAt = System.currentTimeMillis()
                    )
                )
                subject.featureCount++
                subjectBox.put(subject)
            }
        }
    }

    /** Deletes a feature and decrements its subject's feature count. */
    suspend fun deleteFeature(featureId: String) {
        requireInitialized()
        withContext(Dispatchers.IO) {
            boxStore.runInTx {
                // Find feature or throw
                featureBox.query(FeatureEntity_.featureId.equal(featureId)).build().use { query ->
                    val feature = query.findFirst() ?: throw EmbeddingStoreException(
                        message = "Feature not found: $featureId",
                        errorCode = EmbeddingStoreErrorCodes.FEATURE_NOT_FOUND,
                    )
                    val subjectId = feature.subjectId
                    featureBox.remove(feature)
                    // Update subject count
                    val subject = requireSubjectExists(subjectId)
                    subject.featureCount--
                    subjectBox.put(subject)
                }
            }
        }
    }

    /** Returns all feature IDs for the given subject. */
    suspend fun listFeatures(subjectId: String): List<String> {
        requireInitialized()
        return withContext(Dispatchers.IO) {
            requireSubjectExists(subjectId)
            featureBox.query(FeatureEntity_.subjectId.equal(subjectId)).build().find().map { it.featureId }
        }
    }

    //#endregion Feature CRUD

    //#region Recognition

    /** Matches [image] against enrolled subjects and returns the top *k* results. */
    suspend fun recognize(image: Bitmap, k: Int): List<Match> {
        requireInitialized()
        // Extract query vector
        val query = extractor.extract(image)
        return withContext(Dispatchers.IO) {
            // Run ANN search and map to matches
            featureBox.query(FeatureEntity_.vector.nearestNeighbors(query, k)).build().use { annQuery ->
                annQuery.findWithScores().mapNotNull { result ->
                    val record = result.get()
                    val subject = subjectBox.query(SubjectEntity_.subjectId.equal(record.subjectId)).build()
                        .use { it.findFirst() } ?: return@mapNotNull null
                    Match(
                        subjectId = record.subjectId,
                        name = subject.name,
                        confidence = (1f - result.score.toFloat()) * 100f
                    )
                }
            }
        }
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

    private fun requireSubjectExists(subjectId: String): SubjectEntity {
        return subjectBox.query(SubjectEntity_.subjectId.equal(subjectId)).build().findFirst()
            ?: throw EmbeddingStoreException(
                message = "Subject not found: $subjectId",
                errorCode = EmbeddingStoreErrorCodes.SUBJECT_NOT_FOUND,
            )
    }

    private fun mapToSubject(entity: SubjectEntity): Subject {
        return Subject(
            subjectId = entity.subjectId,
            name = entity.name,
            featureCount = entity.featureCount,
            createdAt = entity.createdAt
        )
    }

    //#endregion Helpers
}
