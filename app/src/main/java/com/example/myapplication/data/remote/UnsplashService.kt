package com.example.myapplication.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object UnsplashService {
    private var accessKey: String = ""
    private val cache = ConcurrentHashMap<String, String>()
    private val client = RelayHttp.builder().build()

    fun init(key: String) { accessKey = key }

    suspend fun resolveUrl(query: String): String? = withContext(Dispatchers.IO) {
        cache[query]?.let { return@withContext it }
        if (accessKey.isBlank()) return@withContext null
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.unsplash.com/search/photos" +
                "?query=$encoded&per_page=5&orientation=portrait&content_filter=high&client_id=$accessKey"
            val body = client.newCall(Request.Builder().url(url).build())
                .execute().use { it.body?.string() } ?: return@withContext null
            val results = JSONObject(body).optJSONArray("results")
                ?.takeIf { it.length() > 0 } ?: return@withContext null

            // Prefer images whose description/alt doesn't suggest a person is present
            val personTerms = setOf("woman", "man", "girl", "boy", "model", "person", "people", "wearing", "outfit")
            var best: String? = null
            for (i in 0 until results.length()) {
                val obj = results.getJSONObject(i)
                val desc = (obj.optString("description") + obj.optString("alt_description")).lowercase()
                val url = obj.optJSONObject("urls")?.optString("small") ?: continue
                if (personTerms.none { it in desc }) {
                    best = url
                    break
                }
                if (best == null) best = url  // fallback to first if none match
            }
            val imgUrl = best ?: return@withContext null
            cache[query] = imgUrl
            imgUrl
        } catch (e: Exception) { null }
    }
}
