package com.github.raghavaindrakj.mediapipevision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.raghavaindrakj.mediapipevision.exception.EmbeddingStoreErrorCodes
import com.github.raghavaindrakj.mediapipevision.exception.EmbeddingStoreException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Runs against a real SQLite store and the real MediaPipe embedder on-device, using four
 * near-identical mocktail photos (same glass/straw/ice/garnish, differing only by liquid color)
 * as the hardest realistic case for the embedder to tell apart.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingStoreInstrumentedTest {

    private lateinit var store: EmbeddingStore

    @Before
    fun setUp() = runBlocking {
        store = EmbeddingStore()
        store.initialize(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = runBlocking {
        // Each test method reopens the same on-disk store, so leftover subjects from a prior
        // method (or a prior run) would otherwise contaminate recognize() results in later tests.
        store.listSubjects().forEach { store.deleteSubject(it.subjectId) }
        store.close()
    }

    @Test
    fun recognize_matchesExactSubjectAmongNearIdenticalMocktails() = runBlocking {
        val teal = "teal-${UUID.randomUUID()}"
        val turquoise = "turquoise-${UUID.randomUUID()}"
        val cyan = "cyan-${UUID.randomUUID()}"
        val aquamarine = "aquamarine-${UUID.randomUUID()}"

        store.createSubject(teal, "Teal Mocktail")
        store.createSubject(turquoise, "Turquoise Mocktail")
        store.createSubject(cyan, "Cyan Mocktail")
        store.createSubject(aquamarine, "Aquamarine Mocktail")

        store.createFeature(teal, loadAsset("mocktail_teal.png"))
        store.createFeature(turquoise, loadAsset("mocktail_turquoise.png"))
        store.createFeature(cyan, loadAsset("mocktail_cyan.png"))
        store.createFeature(aquamarine, loadAsset("mocktail_aquamarine.png"))

        val results = store.recognize(loadAsset("mocktail_cyan.png"), k = 4)

        assertEquals(4, results.size)
        assertEquals(cyan, results.first().subjectId)
        assertTrue(
            "expected the exact match's confidence (${results[0].confidence}) to beat the " +
                "closest near-identical runner-up (${results[1].confidence})",
            results[0].confidence > results[1].confidence
        )
    }

    @Test
    fun subjectAndFeatureCrud_roundTrips() = runBlocking {
        val subjectId = "crud-${UUID.randomUUID()}"

        store.createSubject(subjectId, "Test Mocktail")
        val created = store.getSubject(subjectId)
        assertEquals("Test Mocktail", created?.name)
        assertEquals(0, created?.featureCount)

        store.createFeature(subjectId, loadAsset("mocktail_teal.png"))
        assertEquals(1, store.getSubject(subjectId)?.featureCount)

        val featureIds = store.listFeatures(subjectId)
        assertEquals(1, featureIds.size)

        store.deleteFeature(featureIds.first())
        assertEquals(0, store.getSubject(subjectId)?.featureCount)

        store.deleteSubject(subjectId)
        assertNull(store.getSubject(subjectId))
    }

    @Test
    fun createSubject_duplicateId_throwsSubjectAlreadyExists() = runBlocking {
        val subjectId = "dup-${UUID.randomUUID()}"
        store.createSubject(subjectId, "First")

        try {
            store.createSubject(subjectId, "Second")
            fail("expected EmbeddingStoreException")
        } catch (e: EmbeddingStoreException) {
            assertEquals(EmbeddingStoreErrorCodes.SUBJECT_ALREADY_EXISTS, e.errorCode)
        }
    }

    @Test
    fun deleteSubject_removesSubjectAndItsFeatures() = runBlocking {
        val subjectId = "delete-${UUID.randomUUID()}"
        store.createSubject(subjectId, "Disposable Mocktail")
        store.createFeature(subjectId, loadAsset("mocktail_aquamarine.png"))

        store.deleteSubject(subjectId)

        assertNull(store.getSubject(subjectId))
        try {
            store.listFeatures(subjectId)
            fail("expected EmbeddingStoreException")
        } catch (e: EmbeddingStoreException) {
            assertEquals(EmbeddingStoreErrorCodes.SUBJECT_NOT_FOUND, e.errorCode)
        }
    }

    @Test
    fun recognize_logsConfidenceMatrixForAllMocktailImages() = runBlocking {
        val mocktails = listOf("teal", "turquoise", "cyan", "aquamarine")
        val subjectIds = mocktails.associateWith { "$it-${UUID.randomUUID()}" }

        mocktails.forEach { name ->
            store.createSubject(subjectIds.getValue(name), name)
            store.createFeature(subjectIds.getValue(name), loadAsset("mocktail_$name.png"))
        }

        mocktails.forEach { queryName ->
            val results = store.recognize(loadAsset("mocktail_$queryName.png"), k = mocktails.size)
            Log.i(TAG, "query=$queryName")
            results.forEach { match ->
                val matchedName = subjectIds.entries.first { it.value == match.subjectId }.key
                Log.i(TAG, "  -> $matchedName: %.4f%%".format(match.confidence))
            }
        }
    }

    private fun loadAsset(name: String): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().context
        return context.assets.open(name).use { BitmapFactory.decodeStream(it) }
    }

    private companion object {
        const val TAG = "MocktailConfidence"
    }
}
