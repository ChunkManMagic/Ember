package com.ember.companion.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Venice.ai Image Generation Client
 *
 * Environment Variable / Key: VENICE_API_KEY (stored securely in app settings)
 *
 * Recommended models for high quality / good price:
 * - "lustify-v8" or "lustify-sdxl" or "venice-sd35" ($0.01 range, excellent for photoreal characters)
 * - "grok-imagine-image" ($0.03-0.04)
 */
class VeniceImageClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    suspend fun generateImage(
        apiKey: String,
        prompt: String,
        model: String = "lustify-v8",
        width: Int = 1024,
        height: Int = 1024
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Venice API Key is missing. Add it in settings."))

        val payload = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("width", width)
            put("height", height)
            put("format", "webp") // b64_json is sometimes inside a data array, wait, Venice returns raw image or b64?
            // Actually, wait, Venice doc says "Response returns base64 images" 
            put("safe_mode", false)
            put("return_binary", false) // Usually standard to get b64 JSON
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.venice.ai/api/v1/image/generate")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .post(payload)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                // Read the body exactly once, as bytes. The previous version read
                // it as a String and then tried to read it again as bytes in the
                // catch block, which always threw because the body was already
                // consumed and closed — so the raw-image fallback never ran and a
                // binary response surfaced as a confusing exception instead of a
                // decoded bitmap.
                val bytes = response.body?.bytes()
                    ?: return@withContext Result.failure(Exception("Venice returned an empty body."))
                val bodyStr = String(bytes, Charsets.UTF_8)

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${bodyStr.take(300)}"))
                }

                fun decode(candidate: ByteArray): Bitmap? =
                    if (candidate.isEmpty()) null
                    else BitmapFactory.decodeByteArray(candidate, 0, candidate.size)

                // Venice normally answers with base64 JSON, in either {"images":[...]}
                // or the OpenAI-compatible {"data":[{"b64_json":"..."}]}.
                var parsedJson = false
                runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(bodyStr) }
                    .getOrNull()
                    ?.let { element ->
                        if (element is JsonObject) {
                            parsedJson = true
                            // firstOrNull, not get(0): a filtered response arrives as
                            // {"data":[]} and indexing it threw instead of reporting
                            // the filter result the user actually needs to see.
                            val b64 = if (element.containsKey("images")) {
                                element["images"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                            } else {
                                element["data"]?.jsonArray?.firstOrNull()
                                    ?.jsonObject?.get("b64_json")?.jsonPrimitive?.content
                            }
                            if (!b64.isNullOrBlank()) {
                                decode(Base64.decode(b64, Base64.DEFAULT))?.let {
                                    return@withContext Result.success(it)
                                }
                            }
                        }
                    }

                // Not JSON, or JSON without an image: the bytes may be the image
                // itself (which is what happens if the provider honours binary
                // despite return_binary=false).
                decode(bytes)?.let { return@withContext Result.success(it) }

                val reason = if (parsedJson) {
                    "Venice returned no image data."
                } else {
                    "Could not parse image from response: ${bodyStr.take(100)}"
                }
                Result.failure(Exception(reason))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
