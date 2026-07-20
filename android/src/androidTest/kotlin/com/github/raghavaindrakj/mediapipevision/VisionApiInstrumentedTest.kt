package com.github.raghavaindrakj.mediapipevision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.VectorDbErrorCodes
import com.github.raghavaindrakj.mediapipevision.domain.vectordb.VectorDbException
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
class VisionApiInstrumentedTest {

    private lateinit var api: VisionApi

    @Before
    fun setUp() = runBlocking {
        api = VisionApi.create(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = runBlocking {
        api.listSubjects().forEach { api.deleteSubject(it.subjectId) }
        api.close()
    }

    @Test
    fun recognize_matchesExactSubjectAmongNearIdenticalMocktails() = runBlocking {
        val teal = "teal-${UUID.randomUUID()}"
        val turquoise = "turquoise-${UUID.randomUUID()}"
        val cyan = "cyan-${UUID.randomUUID()}"
        val aquamarine = "aquamarine-${UUID.randomUUID()}"

        api.createSubject(teal, "Teal Mocktail")
        api.createSubject(turquoise, "Turquoise Mocktail")
        api.createSubject(cyan, "Cyan Mocktail")
        api.createSubject(aquamarine, "Aquamarine Mocktail")

        api.createFeature(teal, loadAsset("mocktail_teal.png"))
        api.createFeature(turquoise, loadAsset("mocktail_turquoise.png"))
        api.createFeature(cyan, loadAsset("mocktail_cyan.png"))
        api.createFeature(aquamarine, loadAsset("mocktail_aquamarine.png"))

        val results = api.recognize(loadAsset("mocktail_cyan.png"), k = 4)

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

        api.createSubject(subjectId, "Test Mocktail")
        val created = api.getSubject(subjectId)
        assertEquals("Test Mocktail", created?.name)
        assertEquals(0, created?.featureCount)

        api.createFeature(subjectId, loadAsset("mocktail_teal.png"))
        assertEquals(1, api.getSubject(subjectId)?.featureCount)

        val featureIds = api.listFeatures(subjectId)
        assertEquals(1, featureIds.size)

        api.deleteFeature(featureIds.first())
        assertEquals(0, api.getSubject(subjectId)?.featureCount)

        api.deleteSubject(subjectId)
        assertNull(api.getSubject(subjectId))
    }

    @Test
    fun createSubject_duplicateId_throwsSubjectAlreadyExists() = runBlocking {
        val subjectId = "dup-${UUID.randomUUID()}"
        api.createSubject(subjectId, "First")

        try {
            api.createSubject(subjectId, "Second")
            fail("expected VectorDbException")
        } catch (e: VectorDbException) {
            assertEquals(VectorDbErrorCodes.SUBJECT_ALREADY_EXISTS, e.errorCode)
        }
    }

    @Test
    fun deleteSubject_removesSubjectAndItsFeatures() = runBlocking {
        val subjectId = "delete-${UUID.randomUUID()}"
        api.createSubject(subjectId, "Disposable Mocktail")
        api.createFeature(subjectId, loadAsset("mocktail_aquamarine.png"))

        api.deleteSubject(subjectId)

        assertNull(api.getSubject(subjectId))
        try {
            api.listFeatures(subjectId)
            fail("expected VectorDbException")
        } catch (e: VectorDbException) {
            assertEquals(VectorDbErrorCodes.SUBJECT_NOT_FOUND, e.errorCode)
        }
    }

    @Test
    fun recognize_logsConfidenceMatrixForAllMocktailImages() = runBlocking {
        val mocktails = listOf("teal", "turquoise", "cyan", "aquamarine")
        val subjectIds = mocktails.associateWith { "$it-${UUID.randomUUID()}" }

        mocktails.forEach { name ->
            api.createSubject(subjectIds.getValue(name), name)
            api.createFeature(subjectIds.getValue(name), loadAsset("mocktail_$name.png"))
        }

        mocktails.forEach { queryName ->
            val results = api.recognize(loadAsset("mocktail_$queryName.png"), k = mocktails.size)
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
