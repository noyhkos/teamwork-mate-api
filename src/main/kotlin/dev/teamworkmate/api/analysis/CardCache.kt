package dev.teamworkmate.api.analysis

import java.time.Instant
import org.springframework.stereotype.Component

/**
 * In-process cache for rendered share cards.
 *
 * `card.png` previously rebuilt the whole report and called calc on every hit.
 * The `Cache-Control: max-age=3600` header only asks downstream to behave; a
 * crawler that ignores it — and link-preview crawlers routinely do, several
 * arriving at once for the same freshly-posted link — paid the full cost each
 * time.
 *
 * Keyed by `analyzedAt` rather than the slug alone: the slug survives a re-run
 * (so already-shared links keep working), which means the slug on its own would
 * serve a stale card after re-analysis.
 *
 * Deliberately in-process and small. Lambda instances are short-lived, so this
 * collapses a burst against one warm instance rather than acting as a durable
 * cache — that would be a job for S3 or a CDN, not for heap.
 */
@Component
class CardCache {

    private data class Key(val slug: String, val analyzedAt: Instant)

    // ~35KB per card, so the cap bounds this at a couple of MB.
    private val entries = object : LinkedHashMap<Key, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Key, ByteArray>) = size > MAX_ENTRIES
    }

    fun get(slug: String, analyzedAt: Instant, render: () -> ByteArray): ByteArray {
        val key = Key(slug, analyzedAt)
        synchronized(entries) { entries[key] }?.let { return it }

        // Rendered outside the lock: it is an HTTP call to calc, and holding the
        // monitor across it would serialise every other card request behind it.
        // A concurrent duplicate render is cheaper than that.
        val png = render()
        synchronized(entries) { entries[key] = png }
        return png
    }

    companion object {
        const val MAX_ENTRIES = 64
    }
}
