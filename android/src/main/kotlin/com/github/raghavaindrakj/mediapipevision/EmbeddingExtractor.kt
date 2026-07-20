package com.github.raghavaindrakj.mediapipevision

import android.content.Context
import android.graphics.Bitmap
import com.github.raghavaindrakj.mediapipevision.exception.EmbeddingExtractorErrorCodes
import com.github.raghavaindrakj.mediapipevision.exception.EmbeddingExtractorException
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder.ImageEmbedderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Extracts embedding vectors from images. */
internal class EmbeddingExtractor private constructor(
    /** Underlying MediaPipe embedder instance. */
    private val embedder: ImageEmbedder
) {
    /** Guards concurrent access to the embedder. */
    private val mutex = Mutex()

    companion object {
        /** Creates an [EmbeddingExtractor] by loading the bundled TFLite model asset. */
        fun create(context: Context): EmbeddingExtractor {
            val embedder = try {
                val baseOptions = BaseOptions.builder().setModelAssetPath(MODEL_FILE_NAME).build()
                val options = ImageEmbedderOptions.builder().setBaseOptions(baseOptions).build()
                ImageEmbedder.createFromOptions(context, options)
            } catch (e: Exception) {
                throw EmbeddingExtractorException(
                    message = "Failed to initialise the embedder: ${e.message}",
                    errorCode = EmbeddingExtractorErrorCodes.MODEL_FAILED_TO_LOAD,
                    cause = e
                )
            }
            return EmbeddingExtractor(embedder)
        }
    }

    /** Extracts a feature vector from the given [bitmap]. */
    suspend fun extract(bitmap: Bitmap): FloatArray = mutex.withLock {
        withContext(Dispatchers.Default) {
            // Convert bitmap and run inference
            val mpImage = BitmapImageBuilder(bitmap).build()
            val embedding = embedder.embed(mpImage).embeddingResult().embeddings().first()
            val values = embedding.floatEmbedding()
            // Validate output dimensionality
            if (values.size != EMBEDDING_DIMENSIONS.toInt()) {
                throw EmbeddingExtractorException(
                    message = "Embedder produced a ${values.size}-dim vector, expected ${EMBEDDING_DIMENSIONS}-dim.",
                    errorCode = EmbeddingExtractorErrorCodes.UNEXPECTED_EMBEDDING_DIMENSION
                )
            }
            values
        }
    }

    /** Releases native resources. */
    fun close() {
        embedder.close()
    }
}
