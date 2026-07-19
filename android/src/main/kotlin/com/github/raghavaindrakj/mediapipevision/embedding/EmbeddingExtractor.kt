package com.github.raghavaindrakj.mediapipevision.embedding

import android.graphics.Bitmap

internal interface EmbeddingExtractor {
    val embeddingDim: Int

    suspend fun extract(bitmap: Bitmap): FloatArray

    fun close()
}
