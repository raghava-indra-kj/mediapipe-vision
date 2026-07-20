package com.github.raghavaindrakj.mediapipevision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.raghavaindrakj.mediapipevision.infra.vectordb.supabase.EmbedderType
import com.github.raghavaindrakj.mediapipevision.infra.vectordb.supabase.SupabaseVectorDb
import com.github.raghavaindrakj.mediapipevision.infra.vectorizer.mediapipe.MediaPipeVectorizer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration test exercising [MediaPipeVectorizer] with [SupabaseVectorDb] over real HTTP.
 * Enrolls all four mocktail images and cross-recognizes every image against the gallery
 * to measure accuracy and round-trip speed.
 *
 * Skipped unless these Gradle project properties are set:
 *   -PsupabaseUrl=... -PsupabaseKey=...
 */
@RunWith(AndroidJUnit4::class)
class SupabaseMediaPipeIntegrationTest {

    private lateinit var vectorizer: MediaPipeVectorizer
    private lateinit var database: SupabaseVectorDb

    private val mocktails = listOf("teal", "turquoise", "cyan", "aquamarine")

    @Before
    fun setUp() {
        val args = InstrumentationRegistry.getArguments()
        val supabaseUrl = args.getString("supabaseUrl", "")
        val supabaseKey = args.getString("supabaseKey", "")

        assumeTrue("Set supabaseUrl, supabaseKey via -P", supabaseUrl.isNotEmpty() && supabaseKey.isNotEmpty())

        vectorizer = MediaPipeVectorizer.create(ApplicationProvider.getApplicationContext())
        database = SupabaseVectorDb.create(supabaseUrl, supabaseKey, EmbedderType.MEDIAPIPE)
    }

    @After
    fun tearDown() = runBlocking {
        if (::database.isInitialized) {
            database.listSubjects().forEach { database.deleteSubject(it.subjectId) }
            database.close()
        }
        if (::vectorizer.isInitialized) vectorizer.close()
    }

    @Test
    fun crossRecognizeAllMocktails() = runBlocking {
        // Enroll all four mocktail images.
        val subjectIds = mocktails.associateWith { "$it-${UUID.randomUUID()}" }
        val enrolledVectors = mutableMapOf<String, FloatArray>()

        mocktails.forEach { name ->
            val id = subjectIds.getValue(name)
            database.createSubject(id, name)
            val bitmap = loadAsset("mocktail_$name.png")
            val vector = vectorizer.extract(bitmap)
            enrolledVectors[name] = vector
            database.createFeature(id, vector)
            Log.i(TAG, "Enrolled $name ($id)")
        }

        var totalMatches = 0
        var correctMatches = 0
        var totalTimeMs = 0L

        // Cross-recognize: query with every enrolled image.
        mocktails.forEach { queryName ->
            val queryVector = enrolledVectors.getValue(queryName)

            val start = System.nanoTime()
            val results = database.recognize(queryVector, k = mocktails.size)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            totalTimeMs += elapsedMs

            val topMatchId = results.first().subjectId
            val topMatchName = subjectIds.entries.first { it.value == topMatchId }.key
            val isCorrect = topMatchName == queryName
            if (isCorrect) correctMatches++
            totalMatches++

            Log.i(TAG, "query=$queryName -> top=$topMatchName confidence=%.2f%% correct=$isCorrect (${elapsedMs}ms)".format(results.first().confidence))

            results.forEachIndexed { rank, match ->
                val name = subjectIds.entries.first { it.value == match.subjectId }.key
                Log.i(TAG, "  #${rank + 1}: $name %.2f%%".format(match.confidence))
            }
        }

        val accuracy = correctMatches.toFloat() / totalMatches * 100f
        val avgTimeMs = totalTimeMs / totalMatches

        Log.i(TAG, "=== Results ===")
        Log.i(TAG, "Accuracy: $correctMatches/$totalMatches (%.1f%%)".format(accuracy))
        Log.i(TAG, "Avg recognize() time: ${avgTimeMs}ms")

        assert(accuracy >= 75f) { "accuracy ${accuracy}% is below 75% threshold" }
    }

    private fun loadAsset(name: String): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().context
        return context.assets.open(name).use { BitmapFactory.decodeStream(it) }
    }

    private companion object {
        const val TAG = "SupabaseMediaPipeIntegration"
    }
}
