package com.github.raghavaindrakj.mediapipevision.infra.vectordb.supabase

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Minimal HTTP + JSON client for a Supabase project's PostgREST endpoint (`/rest/v1`).
 * Not a general-purpose client — only covers the request shapes needed by the vector store.
 * All methods block the calling thread.
 */
internal class PostgrestClient(
    supabaseUrl: String,
    private val apiKey: String
) {
    /** HTTP client used for all API requests. */
    private val httpClient = OkHttpClient()
    /** Base URL for the PostgREST REST API. */
    private val restUrl = "${normalizeBaseUrl(supabaseUrl)}/rest/v1"

    /** Rows returned by the request, plus the total row count when `Prefer: count=` was requested. */
    class Result(val rows: JSONArray, val totalCount: Long?)

    /** Queries rows from a table. */
    fun select(
        table: String,
        columns: String,
        filters: Map<String, String> = emptyMap(),
        count: Boolean = false,
        head: Boolean = false
    ): Result {
        val url = buildUrl(table, mapOf("select" to columns) + filters.mapValues { "eq.${it.value}" })
        return execute(if (head) "HEAD" else "GET", url, body = null, prefer = if (count) "count=exact" else null)
    }

    /** Inserts a row into a table. */
    fun insert(table: String, row: JSONObject): Result {
        return execute("POST", buildUrl(table, emptyMap()), body = row, prefer = "return=minimal")
    }

    /** Updates rows matching filters. */
    fun update(table: String, filters: Map<String, String>, patch: JSONObject): Result {
        val url = buildUrl(table, filters.mapValues { "eq.${it.value}" })
        return execute("PATCH", url, body = patch, prefer = "return=representation")
    }

    /** Deletes rows matching filters. */
    fun delete(table: String, filters: Map<String, String>): Result {
        val url = buildUrl(table, filters.mapValues { "eq.${it.value}" })
        return execute("DELETE", url, body = null, prefer = "count=exact")
    }

    /** Calls a remote procedure. */
    fun rpc(function: String, params: JSONObject): Result {
        return execute("POST", "$restUrl/rpc/$function", body = params, prefer = null)
    }

    /** Releases pooled connections and background threads. Idempotent. */
    fun close() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    /** Builds the request URL with query parameters. */
    private fun buildUrl(table: String, params: Map<String, String>): String {
        if (params.isEmpty()) return "$restUrl/$table"
        val query = params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return "$restUrl/$table?$query"
    }

    /** Sends the request and parses the response. */
    private fun execute(method: String, url: String, body: JSONObject?, prefer: String?): Result {
        // Build the HTTP request.
        val requestBody = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .header("apikey", apiKey)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .apply { prefer?.let { header("Prefer", it) } }
            .build()

        // Execute and parse the response.
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw parseError(response.code, text)
            }
            // Parse rows from response body.
            val rows = when {
                text.isBlank() -> JSONArray()
                text.trimStart().startsWith("[") -> JSONArray(text)
                else -> JSONArray().put(JSONObject(text))
            }
            // Extract total count from Content-Range header.
            val totalCount = response.header("Content-Range")?.substringAfterLast("/")?.toLongOrNull()
            return Result(rows, totalCount)
        }
    }

    /** Extracts a [PostgrestException] from an error response. */
    private fun parseError(status: Int, body: String): PostgrestException {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val code = json?.optString("code")?.takeIf { it.isNotEmpty() }
        val message = json?.optString("message")?.takeIf { it.isNotEmpty() } ?: "HTTP $status: $body"
        return PostgrestException(code, message)
    }

    /** URL-encodes a string value. */
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object {
        /** Media type for JSON request bodies. */
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** Ensures the base URL has a scheme, defaulting to HTTPS. */
        fun normalizeBaseUrl(supabaseUrl: String): String {
            val trimmed = supabaseUrl.trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }
    }
}
