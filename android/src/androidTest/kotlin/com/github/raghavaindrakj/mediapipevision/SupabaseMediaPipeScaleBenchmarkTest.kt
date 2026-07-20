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
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Measures recognition latency of the Supabase vector store at realistic enrollment scale
 * using real MediaPipe-extracted embeddings from the four mocktail photos.
 *
 * Skipped unless these Gradle project properties are set:
 *   -PsupabaseUrl=... -PsupabaseKey=...
 */
@RunWith(AndroidJUnit4::class)
class SupabaseMediaPipeScaleBenchmarkTest {

    @Test
    fun recognize_at50Dishes100SamplesEach(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val supabaseUrl = args.getString("supabaseUrl", "")
        val supabaseKey = args.getString("supabaseKey", "")

        assumeTrue("Set supabaseUrl, supabaseKey via -P",
            supabaseUrl.isNotEmpty() && supabaseKey.isNotEmpty())

        val dishCount = 5
        val samplesPerDish = 10

        // Extract four real embeddings via on-device MediaPipe.
        val vectorizer = MediaPipeVectorizer.create(ApplicationProvider.getApplicationContext())
        val realVectors = listOf("mocktail_teal.png", "mocktail_turquoise.png", "mocktail_cyan.png", "mocktail_aquamarine.png")
            .map { vectorizer.extract(loadAsset(it)) }
        vectorizer.close()

        val database = SupabaseVectorDb.create(supabaseUrl, supabaseKey, EmbedderType.MEDIAPIPE)

        try {
            val insertStart = System.nanoTime()
            repeat(dishCount) { dishIndex ->
                val subjectId = "bench-subject-$dishIndex-${UUID.randomUUID()}"
                database.createSubject(subjectId, "Dish $dishIndex")
                repeat(samplesPerDish) { sampleIndex ->
                    val vector = realVectors[sampleIndex % realVectors.size]
                    database.createFeature(subjectId, vector)
                }
            }
            val insertMs = (System.nanoTime() - insertStart) / 1_000_000
            val totalFeatures = dishCount * samplesPerDish
            Log.i(TAG, "Enrolled $totalFeatures features ($dishCount dishes x $samplesPerDish samples) in ${insertMs}ms")

            // Warm-up.
            database.recognize(realVectors[0], k = 5)

            val timings = mutableListOf<Long>()
            repeat(5) { callIndex ->
                val start = System.nanoTime()
                val results = database.recognize(realVectors[2], k = 5)
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                timings += elapsedMs
                Log.i(TAG, "recognize() call #$callIndex: ${elapsedMs}ms, top confidence=${results.first().confidence}")
            }
            Log.i(
                TAG,
                "recognize() over $totalFeatures features, ${timings.size} calls: " +
                    "min=${timings.min()}ms max=${timings.max()}ms avg=${timings.average()}ms"
            )
        } finally {
            database.listSubjects().forEach { database.deleteSubject(it.subjectId) }
            database.close()
        }
    }

    private fun loadAsset(name: String): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().context
        return context.assets.open(name).use { BitmapFactory.decodeStream(it) }
    }

    private companion object {
        const val TAG = "SupabaseMediaPipeScale"
    }
}
