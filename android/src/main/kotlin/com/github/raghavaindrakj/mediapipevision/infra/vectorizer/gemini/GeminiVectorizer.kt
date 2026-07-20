package com.github.raghavaindrakj.mediapipevision.infra.vectorizer.gemini

import android.graphics.Bitmap
import android.util.Base64
import com.github.raghavaindrakj.mediapipevision.domain.vectorizer.Vectorizer
import com.github.raghavaindrakj.mediapipevision.domain.vectorizer.VectorizerErrorCodes
import com.github.raghavaindrakj.mediapipevision.domain.vectorizer.VectorizerException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Embedder backed by Gemini's `gemini-embedding-2` model via its REST API. */
class GeminiVectorizer private constructor(
    private val apiKey: String,
    override val dimensions: Int
) : Vectorizer {

    private val httpClient = OkHttpClient()

    @Volatile
    private var embedderReleased = false

    /** Sends [bitmap] as JPEG to Gemini and returns the embedding vector. */
    override suspend fun extract(bitmap: Bitmap): FloatArray = withContext(Dispatchers.IO) {
        // Guard against use after close.
        if (embedderReleased) {
            throw VectorizerException(
                message = "Vectorizer is closed",
                errorCode = VectorizerErrorCodes.CLOSED
            )
        }

        // Build the HTTP request.
        val request = Request.Builder()
            .url("$ENDPOINT:embedContent")
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(requestBody(bitmap).toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        // Execute request and parse embedding.
        val values = try {
            httpClient.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    val errMsg = runCatching { JSONObject(text).getJSONObject("error").optString("message").takeIf { it.isNotEmpty() } }.getOrNull()
                    throw VectorizerException(
                        message = "Gemini embedding request failed (${response.code}): ${errMsg ?: text}",
                        errorCode = VectorizerErrorCodes.EXTRACTION_FAILED
                    )
                }
                JSONObject(text).getJSONArray("embeddings").getJSONObject(0).getJSONArray("values")
            }
        } catch (e: VectorizerException) {
            throw e
        } catch (e: Exception) {
            throw VectorizerException(
                message = "Gemini embedding request failed: ${e.message}",
                errorCode = VectorizerErrorCodes.EXTRACTION_FAILED,
                cause = e
            )
        }

        // Validate dimensionality.
        if (values.length() != dimensions) {
            throw VectorizerException(
                message = "Vectorizer produced a ${values.length()}-dim vector, expected $dimensions-dim.",
                errorCode = VectorizerErrorCodes.UNEXPECTED_DIMENSION
            )
        }
        FloatArray(values.length()) { i -> values.getDouble(i).toFloat() }
    }

    /** Releases HTTP connections. Idempotent. */
    override fun close() {
        if (embedderReleased) return
        embedderReleased = true
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    /** Builds the JSON body with bitmap as base64 JPEG. */
    private fun requestBody(bitmap: Bitmap): JSONObject {
        // Compress bitmap to JPEG bytes.
        val jpegBytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }

        // Encode as base64.
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

        // Build JSON payload.
        return JSONObject()
            .put(
                "content",
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put(
                            "inline_data",
                            JSONObject()
                                .put("mime_type", "image/jpeg")
                                .put("data", base64)
                        )
                    )
                )
            )
            .put("output_dimensionality", dimensions)
    }

    companion object {
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2"
        private const val JPEG_QUALITY = 92
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun create(apiKey: String, dimensions: Int = 1536): GeminiVectorizer {
            require(dimensions in 128..3072) { "dimensions must be in 128..3072, was $dimensions" }
            return GeminiVectorizer(apiKey, dimensions)
        }
    }
}