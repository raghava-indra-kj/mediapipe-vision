package com.github.raghavaindrakj.mediapipevision.domain.vectorizer

import android.graphics.Bitmap

interface Vectorizer {
    val dimensions: Int

    suspend fun extract(bitmap: Bitmap): FloatArray

    fun close()
}
