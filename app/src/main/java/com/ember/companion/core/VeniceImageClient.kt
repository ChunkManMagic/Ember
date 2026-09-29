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
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $bodyStr"))
                }

                // If it's pure binary image (some APIs do this if format is webp and not b64 json)
                // But OpenAI compatible usually returns {"data": [{"b64_json": "..."}]}
                // Let's try parsing as JSON first
                try {
                    val json = Json { ignoreUnknownKeys = true }
                    val element = json.parseToJsonElement(bodyStr)
                    var b64: String? = null
                    
                    if (element is JsonObject) {
                        if (element.containsKey("images")) {
                            b64 = element["images"]?.jsonArray?.get(0)?.jsonPrimitive?.content
                        } else if (element.containsKey("data")) {
                            b64 = element["data"]?.jsonArray?.get(0)?.jsonObject?.get("b64_json")?.jsonPrimitive?.content
                        }
                    }
                    
                    if (b64 != null) {
                        val decodedString = Base64.decode(b64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                        if (bitmap != null) {
                            return@withContext Result.success(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    // It might be raw binary data (e.g. if return_binary=true was implicit)
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            return@withContext Result.success(bitmap)
                        }
                    }
                }
                return@withContext Result.failure(Exception("Could not parse image from response: ${bodyStr.take(100)}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
