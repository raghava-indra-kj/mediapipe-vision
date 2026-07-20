package com.github.raghavaindrakj.mediapipevision.infra.vectorizer.mediapipe

import android.content.Context
import android.graphics.Bitmap
import com.github.raghavaindrakj.mediapipevision.domain.vectorizer.Vectorizer
import com.github.raghavaindrakj.mediapipevision.domain.vectorizer.VectorizerErrorCodes
import com.github.raghavaindrakj.mediapipevision.domain.vectorizer.VectorizerException
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder.ImageEmbedderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** MediaPipe-based implementation of [Vectorizer]. */
internal class MediaPipeVectorizer private constructor(
    private val embedder: ImageEmbedder
) : Vectorizer {

    /** Guards concurrent access to the embedder. */
    private val mutex = Mutex()

    @Volatile
    private var embedderReleased = false

    /** Length of the feature vector produced by the underlying model. */
    override val dimensions: Int get() = VECTOR_DIMENSIONS

    /** Converts a bitmap into a feature vector. */
    override suspend fun extract(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        mutex.withLock {
            // Guard against use after close.
            // Guard against use after close.
            if (embedderReleased) {
                throw VectorizerException(
                    message = "Vectorizer is closed",
                    errorCode = VectorizerErrorCodes.CLOSED
                )
            }

            // Run inference via MediaPipe ImageEmbedder.
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = embedder.embed(mpImage).embeddingResult().embeddings().firstOrNull()

            // Ensure an embedding was produced.
            val embedding = result ?: throw VectorizerException(
                message = "Vectorizer returned no embedding",
                errorCode = VectorizerErrorCodes.UNEXPECTED_DIMENSION
            )

            // Validate output dimensionality against the expected model output.
            val values = embedding.floatEmbedding()
            if (values.size != VECTOR_DIMENSIONS) {
                throw VectorizerException(
                    message = "Vectorizer produced a ${values.size}-dim vector, expected $VECTOR_DIMENSIONS-dim.",
                    errorCode = VectorizerErrorCodes.UNEXPECTED_DIMENSION
                )
            }
            values
        }
    }

    /** Releases the underlying MediaPipe embedder. Idempotent. */
    override fun close() {
        if (embedderReleased) return
        embedderReleased = true
        embedder.close()
    }

    companion object {
        private const val MODEL_FILE_NAME = "mobilenet_v3_large.tflite"
        private const val VECTOR_DIMENSIONS = 1280

        /** Creates an instance by loading the TFLite model asset. */
        fun create(context: Context): MediaPipeVectorizer {
            val embedder = try {
                val baseOptions = BaseOptions.builder().setModelAssetPath(MODEL_FILE_NAME).build()
                val options = ImageEmbedderOptions.builder().setBaseOptions(baseOptions).setL2Normalize(true).build()
                ImageEmbedder.createFromOptions(context, options)
            } catch (e: Exception) {
                throw VectorizerException(
                    message = "Failed to initialise the vectorizer: ${e.message}",
                    errorCode = VectorizerErrorCodes.MODEL_FAILED_TO_LOAD,
                    cause = e
                )
            }
            return MediaPipeVectorizer(embedder)
        }
    }
}
