package com.github.raghavaindrakj.mediapipevision.embedding

import android.content.Context
import android.graphics.Bitmap
import com.github.raghavaindrakj.mediapipevision.model.MpvRecognitionException
import com.github.raghavaindrakj.mediapipevision.storage.EMBEDDING_DIMENSIONS
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder.ImageEmbedderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [EmbeddingExtractor] backed by MediaPipe Tasks Vision's on-device image embedder, running the
 * bundled MobileNetV3-Large model. Requires no training or export step — the model ships
 * pretrained and is used as-is.
 */
internal class MediaPipeEmbeddingExtractor private constructor(
    private val embedder: ImageEmbedder
) : EmbeddingExtractor {

    companion object {
        private const val MODEL_ASSET_NAME = "mobilenet_v3_large.tflite"

        fun create(context: Context): MediaPipeEmbeddingExtractor {
            val embedder = try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET_NAME)
                    .build()
                val options = ImageEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .build()
                ImageEmbedder.createFromOptions(context, options)
            } catch (e: Exception) {
                throw MpvRecognitionException.ModelLoadFailure(e)
            }
            return MediaPipeEmbeddingExtractor(embedder)
        }
    }

    override val embeddingDim: Int = EMBEDDING_DIMENSIONS.toInt()

    override suspend fun extract(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val embedding = embedder.embed(mpImage).embeddingResult().embeddings().first()
        val values = embedding.floatEmbedding()
        requireExpectedDimension(values.size)
        values
    }

    override fun close() {
        embedder.close()
    }

    private fun requireExpectedDimension(actual: Int) {
        if (actual != EMBEDDING_DIMENSIONS.toInt()) {
            throw MpvRecognitionException.ModelLoadFailure(
                IllegalStateException(
                    "Embedder produced a $actual-dim vector but the store expects ${EMBEDDING_DIMENSIONS}-dim."
                )
            )
        }
    }
}
