package com.ember.companion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

/**
 * Reads a platform's character-creation form so Ember can offer the fields that
 * site actually asks for, instead of assuming every site wants the same ones.
 *
 * This runs on the phone against a page the user names. Ember has no server, so
 * there is no proxy and no shared cache — every fetch is one request, and a
 * failure is reported honestly rather than papered over with guesses.
 */
object PlatformFieldFetcher {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        // Several frontends reject requests with no identifying UA outright.
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(request)
        }
        .build()

    @JvmStatic
    suspend fun fetchFields(url: String): List<FormField> {
        val normalised = normaliseUrl(url)
        val request = Request.Builder().url(normalised).get().build()
        // The blocking call and the Jsoup parse both belong off the main thread.
        // Callers reach this from viewModelScope (Main), so without the switch the
        // whole 20-second read timeout ran on the UI thread.
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("${response.code} from $normalised")
                }
                val body = response.body?.string()
                    ?: throw IllegalStateException("Empty response from $normalised")
                parseFormFields(body)
            }
        }
    }

    /** Accepts a bare host and guesses https, the way a browser would. */
    fun normaliseUrl(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Enter a platform URL first" }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return java.net.URI(withScheme).toString()
    }

    internal fun parseFormFields(html: String): List<FormField> {
        val doc: Document = Jsoup.parse(html)
        val seen = mutableSetOf<String>()
        val out = mutableListOf<FormField>()

        // Scoped to <form> first (the real creation form), then anything left
        // over, so a page whose form is built by JS still yields something.
        val scoped = doc.select("form input, form textarea, form select")
        val controls = if (scoped.isNotEmpty()) scoped else doc.select("input, textarea, select")

        for (el in controls) {
            if (!isMeaningful(el)) continue
            val name = el.attr("name").ifBlank { el.attr("id") }.trim()
            if (name.isEmpty() || !seen.add(name)) continue

            val type = when {
                el.tagName() == "textarea" -> "textarea"
                el.tagName() == "select" -> "select"
                else -> el.attr("type").ifBlank { "text" }.lowercase()
            }
            if (type in IGNORED_INPUT_TYPES) continue

            val options = if (type == "select") {
                el.select("option").mapNotNull { it.text().trim().ifBlank { null } }.take(40)
            } else {
                emptyList()
            }

            out += FormField(
                name = name,
                type = type,
                label = labelFor(doc, el),
                example = el.attr("placeholder").ifBlank { el.attr("value").ifBlank { null } },
                // maxlength is the only cap a form actually publishes; anything
                // else would be a guess.
                maxChars = el.attr("maxlength").toIntOrNull() ?: 0,
                required = el.hasAttr("required") || el.attr("aria-required") == "true",
                options = options,
            )
        }
        return out
    }

    /** Resolves a human label: <label for>, an ancestor <label>, or aria-label. */
    private fun labelFor(doc: Document, el: Element): String {
        val id = el.id()
        if (id.isNotEmpty()) {
            doc.select("label[for=$id]").firstOrNull()?.let { l ->
                l.text().trim().ifBlank { null }?.let { return it }
            }
        }
        el.closest("label")?.text()?.trim()?.ifBlank { null }?.let { return it }
        el.attr("aria-label").trim().ifBlank { null }?.let { return it }
        return ""
    }

    /** Drops buttons, hidden inputs, file pickers and CSRF tokens. */
    private fun isMeaningful(el: Element): Boolean {
        val type = el.attr("type").lowercase()
        if (type in IGNORED_INPUT_TYPES) return false
        if (el.hasAttr("hidden")) return false
        val cls = el.attr("class").lowercase()
        if (cls.contains("csrf") || cls.contains("honeypot")) return false
        val id = el.id().lowercase()
        val name = el.attr("name").lowercase()
        if (id.contains("csrf") || name.contains("csrf") || name.contains("authenticity_token")) return false
        return true
    }

    private val IGNORED_INPUT_TYPES = setOf(
        "submit", "button", "image", "reset", "hidden", "file", "checkbox", "radio",
    )
}