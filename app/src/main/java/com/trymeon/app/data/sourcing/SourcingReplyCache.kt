package com.trymeon.app.data.sourcing

import com.trymeon.app.domain.model.ClothingCategory
import java.util.concurrent.TimeUnit

/**
 * Remembers what the model said about an English phrase.
 *
 * Translating "cropped linen blazer" costs a chat call and about twelve
 * seconds, and the answer does not change between one search and the next. The
 * raw reply is cached rather than the parsed query because parsing also folds
 * in quantity — caching the finished object would charge for a fresh
 * translation just because someone asked for two of something.
 *
 * Policy only: no Android, no storage, so the eviction and expiry rules can be
 * tested directly. [SourcingReplyStore] persists whatever this holds.
 */
class SourcingReplyCache(
    private val maxEntries: Int = 60,
    private val ttlMillis: Long = TimeUnit.DAYS.toMillis(30),
    private val now: () -> Long = System::currentTimeMillis
) {
    data class Entry(val reply: String, val storedAtMillis: Long)

    // Access-ordered so eviction drops the phrase nobody has searched in longest.
    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > maxEntries
    }

    /**
     * Cache key. Case and spacing are noise a shopper introduces without
     * meaning to, so "Cropped Linen Blazer " must reuse "cropped linen blazer".
     * Gender and category hint do change the answer and stay in the key.
     */
    fun key(description: String, gender: String, categoryHint: ClothingCategory?): String {
        val normalised = description.trim().lowercase().replace(Regex("\\s+"), " ")
        return "$normalised|${gender.trim().lowercase()}|${categoryHint?.name.orEmpty()}"
    }

    @Synchronized
    fun get(key: String): String? {
        val entry = entries[key] ?: return null
        // Seller vocabulary drifts; a very old translation is worse than a new call.
        if (now() - entry.storedAtMillis >= ttlMillis) {
            entries.remove(key)
            return null
        }
        return entry.reply
    }

    @Synchronized
    fun put(key: String, reply: String) {
        // Caching a refusal or a truncated answer would pin the failure for a month.
        if (reply.isBlank() || SourcingQueryParser.parse(reply, null) == null) return
        entries[key] = Entry(reply, now())
    }

    @Synchronized
    fun snapshot(): Map<String, Entry> = LinkedHashMap(entries)

    @Synchronized
    fun restore(saved: Map<String, Entry>) {
        entries.clear()
        val cutoff = now() - ttlMillis
        saved.entries
            .filter { it.value.storedAtMillis > cutoff }
            .sortedBy { it.value.storedAtMillis }
            .takeLast(maxEntries)
            .forEach { entries[it.key] = it.value }
    }

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun clear() = entries.clear()
}

/** Somewhere to keep the cache between launches. */
interface SourcingReplyStore {
    fun load(): Map<String, SourcingReplyCache.Entry>
    fun save(entries: Map<String, SourcingReplyCache.Entry>)
}
