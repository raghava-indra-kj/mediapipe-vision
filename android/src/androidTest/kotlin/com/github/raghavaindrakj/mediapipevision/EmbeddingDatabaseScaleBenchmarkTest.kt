package com.github.raghavaindrakj.mediapipevision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Measures [EmbeddingDatabase.recognize] at realistic enrollment scale using genuinely
 * MediaPipe-extracted embeddings from the four real mocktail photos (not fabricated vectors),
 * replicated across many subjects/samples — there's no way to obtain thousands of distinct real
 * dish photos, so real embeddings reused at volume is the honest way to reach that scale.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingDatabaseScaleBenchmarkTest {

    @Test
    fun recognize_at50Dishes100SamplesEach(): Unit = runBlocking {
        val dishCount = 50
        val samplesPerDish = 100

        val extractor = EmbeddingExtractor.create(ApplicationProvider.getApplicationContext())
        val realVectors = listOf("mocktail_teal.png", "mocktail_turquoise.png", "mocktail_cyan.png", "mocktail_aquamarine.png")
            .map { extractor.extract(loadAsset(it)) }
        extractor.close()

        val database = EmbeddingDatabase()
        database.initialize(ApplicationProvider.getApplicationContext())

        try {
            val insertStart = System.nanoTime()
            repeat(dishCount) { dishIndex ->
                val subjectId = "dish-$dishIndex-${UUID.randomUUID()}"
                database.createSubject(subjectId, "Dish $dishIndex")
                repeat(samplesPerDish) { sampleIndex ->
                    val vector = realVectors[sampleIndex % realVectors.size]
                    database.createFeature(subjectId, "feature-${UUID.randomUUID()}", vector)
                }
            }
            val insertMs = (System.nanoTime() - insertStart) / 1_000_000
            val totalFeatures = dishCount * samplesPerDish
            Log.i(TAG, "Enrolled $totalFeatures features ($dishCount dishes x $samplesPerDish samples) in ${insertMs}ms")

            // Warm-up call so the first timed call isn't paying one-off cache/JIT costs.
            database.recognize(realVectors[0], k = 5)

            val timings = mutableListOf<Long>()
            repeat(5) { callIndex ->
                val start = System.nanoTime()
                val results = database.recognize(realVectors[2], k = 5) // query with the real "cyan" embedding
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
        const val TAG = "SqliteScaleBenchmark"
    }
}
