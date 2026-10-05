package com.ember.companion.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The JavaScript Ember injects into a loaded page to harvest media metadata.
 *
 * Why JS and not HTML parsing in Kotlin: the page is already loaded in the
 * WebView, and its cookies/session are what got it past any interstitial.
 * Reading `document` here means we see exactly what the site decided to show,
 * and we never issue a second, unauthenticated request that would get blocked.
 *
 * It collects, in priority order:
 *  1. schema.org JSON-LD `VideoObject`/`ImageObject`/`AudioObject` nodes
 *     (including @graph containers) — real title, runtime, poster, dimensions.
 *  2. OpenGraph / Twitter card tags as a fallback for pages without JSON-LD.
 *  3. Bare <video>/<img>/<source> srcs, filtered down to things that look like
 *     real media rather than tracking pixels, icons or sprites.
 *
 * Returned as a JSON array of objects; parsed by [PageMedia] on the Kotlin side.
 */
object PageMediaScan {

    val SCRIPT: String = """
(function () {
  function abs(u) {
    if (!u) return '';
    u = String(u).trim();
    if (!u) return '';
    if (u.indexOf('//') === 0) return 'https:' + u;
    return u;
  }
  // Tracking pixels, spacers, icons and sprite sheets are not media anyone
  // wants; keeping them turns the results list into noise.
  function isJunk(u) {
    if (!u) return true;
    var s = u.toLowerCase();
    if (s.indexOf('data:') === 0) return true;
    var junk = ['pixel', '1x1', 'blank.gif', 'spacer', 'sprite', 'beacon', 'analytics',
                'doubleclick', 'googlesyndication', 'adservice', 'favicon', '/emoji',
                'loader', 'placeholder', 'tracking', 'badge_', 'icon-', '/icons/'];
    for (var i = 0; i < junk.length; i++) { if (s.indexOf(junk[i]) !== -1) return true; }
    return false;
  }
  function looksLikeVideo(u) {
    var s = u.toLowerCase().split('?')[0];
    // m3u8/m3u are HLS manifests: a playlist, not the video itself, but it is
    // the only handle on the real stream on most players, so it must survive
    // the scan or the page looks empty while a video is right there.
    return /\.(mp4|m4v|webm|mov|mkv|avi|flv|m3u8|m3u)$/.test(s);
  }
  function looksLikeImage(u) {
    var s = u.toLowerCase().split('?')[0];
    return /\.(jpe?g|png|webp|gif|bmp)$/.test(s);
  }
  function meta(prop) {
    var el = document.querySelector('meta[property="' + prop + '"]') ||
              document.querySelector('meta[name="' + prop + '"]');
    return el ? (el.getAttribute('content') || '').trim() : '';
  }

  var out = [];
  var seen = {};

  function push(o) {
    var u = o && o.url ? abs(o.url) : '';
    if (!u || u.indexOf('http') !== 0 || isJunk(u)) return;
    var key = u.split('#')[0];
    if (seen[key]) return;
    seen[key] = true;
    if (o.thumbnailUrl) o.thumbnailUrl = abs(o.thumbnailUrl);
    out.push(o);
  }

  function fromLd(node) {
    if (!node || typeof node !== 'object') return;
    if (Array.isArray(node)) { node.forEach(fromLd); return; }
    if (node['@graph']) { fromLd(node['@graph']); }
    var type = String(node['@type'] || '');
    if (Array.isArray(type)) type = type.join(',');
    var contentUrl = node.contentUrl || node.embedUrl || node.url || '';
    var thumb = node.thumbnailUrl || '';
    if (Array.isArray(thumb)) thumb = thumb[0];
    else if (thumb && typeof thumb === 'object') thumb = thumb.url || '';
    if (!contentUrl && !thumb) return;
    var w = parseInt(node.width || 0, 10) || 0;
    var h = parseInt(node.height || 0, 10) || 0;
    push({
      url: contentUrl || thumb,
      type: type,
      title: node.name || '',
      description: node.description || '',
      thumbnailUrl: thumb,
      duration: node.duration || '',
      width: w,
      height: h,
      uploadDate: node.uploadDate || '',
      fromMetadata: true
    });
  }

  // 1. JSON-LD
  try {
    var scripts = document.querySelectorAll('script[type="application/ld+json"]');
    for (var i = 0; i < scripts.length; i++) {
      try { fromLd(JSON.parse(scripts[i].textContent || '')); }
      catch (e) { /* malformed block, skip */ }
    }
  } catch (e) {}

  // 2. OpenGraph / Twitter card fallback for pages with no JSON-LD
  if (out.length === 0) {
    var ogVideo = meta('og:video') || meta('og:video:url') || meta('og:video:secure_url') ||
                  meta('twitter:player') || meta('twitter:player:stream');
    var ogImage = meta('og:image') || meta('og:image:url') || meta('twitter:image');
    var primary = ogVideo || ogImage;
    if (primary) {
      push({
        url: primary,
        type: ogVideo ? 'VideoObject' : 'ImageObject',
        title: meta('og:title') || meta('twitter:title') || document.title || '',
        description: meta('og:description') || meta('twitter:description') || '',
        thumbnailUrl: ogImage || '',
        duration: meta('video:duration') || '',
        width: parseInt(meta('og:video:width') || meta('video:width') || 0, 10) || 0,
        height: parseInt(meta('og:video:height') || meta('video:height') || 0, 10) || 0,
        uploadDate: meta('article:published_time') || meta('og:video:release_date') || '',
        siteName: meta('og:site_name') || '',
        fromMetadata: true
      });
    }
  }

  // 3. Bare DOM media, only for things that are clearly downloadable files.
  try {
    var vids = document.querySelectorAll('video source[src], video[src]');
    for (var j = 0; j < vids.length; j++) {
      var vs = vids[j].getAttribute('src');
      if (vs && looksLikeVideo(vs)) push({ url: vs, type: 'VideoObject', title: '', description: '', thumbnailUrl: '', duration: '', width: 0, height: 0, uploadDate: '', fromMetadata: false });
    }
    var imgs = document.querySelectorAll('img[src], img[data-src]');
    for (var k = 0; k < imgs.length; k++) {
      var is = imgs[k].getAttribute('src') || imgs[k].getAttribute('data-src');
      if (!is || !looksLikeImage(is)) continue;
      if (isJunk(is)) continue;
      push({ url: is, type: 'ImageObject', title: imgs[k].getAttribute('alt') || '', description: '', thumbnailUrl: is, duration: '', width: parseInt(imgs[k].getAttribute('width') || 0, 10) || 0, height: parseInt(imgs[k].getAttribute('height') || 0, 10) || 0, uploadDate: '', fromMetadata: false });
    }
  } catch (e) {}

  return JSON.stringify(out);
})()
""".trimIndent()

    /**
     * Minimal, tolerant parser for what [SCRIPT] returns. The script is the
     * untrusted side of this boundary (a page can return anything, including
     * garbage or a string), so nothing here throws and a bad entry is skipped
     * rather than failing the whole scan.
     */
    fun parse(rawJson: String?, pageUrl: String): List<PageMedia> {
        if (rawJson.isNullOrBlank()) return emptyList()
        val unescaped = decodeJsString(rawJson)
        return try {
            val root = org.json.JSONTokener(unescaped).nextValue()
            // The injected script always returns an array, but a page can do
            // anything — accept a lone object too rather than losing a real hit.
            val nodes: List<JSONObject> = when (root) {
                is JSONArray -> (0 until root.length()).mapNotNull { root.optJSONObject(it) }
                is JSONObject -> listOf(root)
                else -> emptyList()
            }
            val result = mutableListOf<PageMedia>()
            for (node in nodes) {
                val url = node.optString("url", "").trim()
                // Only real remote URLs: no javascript:, file:, or data: URLs
                // ever reach the download manager.
                if (!url.startsWith("http://") && !url.startsWith("https://")) continue
                result += if (node.optBoolean("fromMetadata", false)) {
                    val width = node.optInt("width", 0)
                    val height = node.optInt("height", 0)
                    PageMedia(
                        url = url,
                        type = node.optString("type", ""),
                        title = node.optString("title", "").trim(),
                        description = node.optString("description", "").trim(),
                        thumbnailUrl = readThumbnail(node),
                        durationMs = PageMedia.parseIsoDuration(node.optString("duration", "")),
                        width = width,
                        height = height,
                        uploadDate = node.optString("uploadDate", "").trim(),
                        siteName = node.optString("siteName", "").trim(),
                        mimeType = PageMedia.mimeFor(url),
                        fromMetadata = true,
                    )
                } else {
                    PageMedia.bareUrl(url)
                }
            }
            dedupe(result)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * schema.org allows thumbnailUrl as a string, an ImageObject, or an array of
     * either. The injected script normalises it, but this stays tolerant because
     * the payload is page-controlled.
     */
    private fun readThumbnail(node: JSONObject): String = when (val th = node.opt("thumbnailUrl")) {
        is String -> th.trim()
        is JSONObject -> th.optString("url", "").trim()
        is JSONArray -> th.optJSONObject(0)?.optString("url", "").orEmpty().trim()
        else -> ""
    }

    /** Same URL twice (query strings and #fragments vary) — keep the richest entry. */
    private fun dedupe(items: List<PageMedia>): List<PageMedia> {
        val best = LinkedHashMap<String, PageMedia>()
        for (item in items) {
            val key = item.url.substringBefore('#')
            val existing = best[key]
            if (existing == null) {
                best[key] = item
            } else if (existing.title.isBlank() && item.title.isNotBlank()) {
                best[key] = item
            } else if (existing.durationMs == 0L && item.durationMs > 0L) {
                best[key] = existing.copy(
                    title = existing.title.ifBlank { item.title },
                    durationMs = item.durationMs,
                    thumbnailUrl = existing.thumbnailUrl.ifBlank { item.thumbnailUrl },
                )
            }
        }
        // Videos first — that's what Ember is mostly for — then anything with a
        // title, then the rest. Metadata-rich entries float to the top.
        return best.values.sortedWith(
            compareByDescending<PageMedia> { it.isVideo }
                .thenByDescending { it.fromMetadata }
                .thenByDescending { it.durationMs > 0 }
                .thenByDescending { it.title.isNotBlank() },
        )
    }

    /** Undo the double-encoding `evaluateJavascript` applies to string returns. */
    private fun decodeJsString(raw: String): String {
        var s = raw.trim()
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length - 1)
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
    }
}