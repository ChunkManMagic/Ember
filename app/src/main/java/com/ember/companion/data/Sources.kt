package com.ember.companion.data

data class Source(
    val name: String,
    val url: String,
    val category: String,
    val note: String = "",
)

/**
 * Ember's launcher list: legal, established, age-gated destinations plus the
 * search engines that can be told to stop filtering adult results.
 *
 * Deliberately absent: any source that aggregates leaked or non-consensional
 * material, and art/fan communities where characters' ages are frequently
 * ambiguous. That is a line Ember holds rather than a missing feature.
 */
object Sources {

    const val CATEGORY_AGGREGATORS = "Video sites"
    const val CATEGORY_TEXT = "Writing & social"
    const val CATEGORY_ART = "Art"
    const val CATEGORY_SEARCH = "Search"

    val all: List<Source> = listOf(
        Source("Pornhub", "https://www.pornhub.com/", CATEGORY_AGGREGATORS, "Large catalogue, studio + creator channels"),
        Source("XVideos", "https://www.xvideos.com/", CATEGORY_AGGREGATORS, "Wide-ranging independent catalogue"),
        Source("XNXX", "https://www.xnxx.com/", CATEGORY_AGGREGATORS, "Short-form and creator uploads"),
        Source("RedTube", "https://www.redtube.com/", CATEGORY_AGGREGATORS, "Studio catalogue, tidy categories"),
        Source("YouPorn", "https://www.youporn.com/", CATEGORY_AGGREGATORS, "Creator-focused, good search"),

        Source("Literotica", "https://www.literotica.com/", CATEGORY_TEXT, "Long-form adult fiction, strong tagging"),
        Source("FetLife", "https://fetlife.com/", CATEGORY_TEXT, "Kink and alternative-relationship networking"),

        Source("DeviantArt", "https://www.deviantart.com/", CATEGORY_ART, "Turn on mature content in settings"),

        Source("DuckDuckGo", "https://duckduckgo.com/", CATEGORY_SEARCH, "Turn off Safe Search on its settings page"),
        Source("Bing", "https://www.bing.com/", CATEGORY_SEARCH, "Turn off the adult-content filter under Settings"),
        Source("Startpage", "https://www.startpage.com/", CATEGORY_SEARCH, "Needs Safe Search disabled in preferences"),
    )

    val categories: List<String> = listOf(
        CATEGORY_AGGREGATORS, CATEGORY_TEXT, CATEGORY_ART, CATEGORY_SEARCH,
    )

    fun byCategory(category: String): List<Source> = all.filter { it.category == category }

    /**
     * Search entry points. Ember never calls a search provider's API; it just
     * hands the query to the in-app WebView.
     */
    fun searchUrl(engine: SearchEngine, query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return engine.home
        if (q.startsWith("http://") || q.startsWith("https://")) return q
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        return when (engine) {
            SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/?q=$encoded&kp=-2"
            SearchEngine.BING -> "https://www.bing.com/search?q=$encoded&adlt=strict"
            SearchEngine.STARTPAGE -> "https://www.startpage.com/sp/search?query=$encoded"
            SearchEngine.PORNHUB -> "https://www.pornhub.com/video/search?search=$encoded"
            SearchEngine.XNXX -> "https://www.xnxx.com/search/$encoded"
        }
    }
}

enum class SearchEngine(val label: String, val home: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/"),
    BING("Bing", "https://www.bing.com/"),
    STARTPAGE("Startpage", "https://www.startpage.com/"),
    PORNHUB("Pornhub", "https://www.pornhub.com/"),
    XNXX("XNXX", "https://www.xnxx.com/"),
}
