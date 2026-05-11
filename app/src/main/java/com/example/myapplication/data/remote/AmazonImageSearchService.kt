package com.example.myapplication.data.remote

import java.util.concurrent.ConcurrentHashMap

/**
 * Thin singleton wrapper around AmazonApiService for single-image lookups.
 * Used by FashionImage to resolve "amazon:<query>" paths in wardrobe essentials.
 */
object AmazonImageSearchService {

    private var accessKey: String = ""
    private var secretKey: String = ""
    private var associateTag: String = ""
    private val cache = ConcurrentHashMap<String, String>()
    private val api = AmazonApiService()

    fun init(accessKey: String, secretKey: String, associateTag: String) {
        this.accessKey = accessKey
        this.secretKey = secretKey
        this.associateTag = associateTag
    }

    val isConfigured get() = accessKey.isNotBlank() && secretKey.isNotBlank() && associateTag.isNotBlank()

    suspend fun resolveUrl(query: String): String? {
        cache[query]?.let { return it }
        if (!isConfigured) return null
        // Try the full query first, then a stripped version (remove gender/modifiers)
        val queries = buildList {
            add(query)
            val stripped = query.replace(Regex("(?i)\\b(women|men|girls|boys|ladies)\\b"), "").trim()
            if (stripped != query) add(stripped)
        }
        for (q in queries) {
            val url = api.search(accessKey, secretKey, associateTag, q, itemCount = 5)
                .getOrNull()
                ?.firstOrNull { it.imageUrl.isNotEmpty() }
                ?.imageUrl
            if (url != null) {
                cache[query] = url
                return url
            }
        }
        return null
    }
}
