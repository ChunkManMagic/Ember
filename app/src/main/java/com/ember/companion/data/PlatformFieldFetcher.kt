package com.ember.companion.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object PlatformFieldFetcher {
    private val client = OkHttpClient()

    @JvmStatic
    suspend fun fetchFields(url: String): List<FormField> {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to fetch $url: ${response.code}")
            }
            val body = response.body?.string() ?: throw IllegalStateException("Empty response body for $url")
            parseFormFields(body)
        }
    }

    private fun parseFormFields(html: String): List<FormField> {
        val doc: Document = Jsoup.parse(html)
        val elements: List<Element> = doc.select("form input, form textarea, form select")
        return elements.mapNotNull { el ->
            val name = el.attr("name").ifBlank { return@mapNotNull null }
            val type = when {
                el.tagName() == "textarea" -> "textarea"
                el.tagName() == "select" -> "select"
                else -> el.attr("type").ifBlank { "text" }
            }
            val example = el.attr("placeholder").ifBlank { el.attr("value").ifBlank { null } }
            FormField(name = name, type = type, example = example)
        }
    }
}
