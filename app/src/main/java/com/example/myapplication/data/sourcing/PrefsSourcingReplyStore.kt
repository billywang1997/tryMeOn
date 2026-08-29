package com.example.myapplication.data.sourcing

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists translated queries in SharedPreferences.
 *
 * Small enough to belong there: sixty short replies, and losing them costs
 * nothing but a re-translation. Gson because the rest of the app already
 * serialises this way.
 */
class PrefsSourcingReplyStore(context: Context) : SourcingReplyStore {

    private val prefs = context.getSharedPreferences("sourcing_replies", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<Map<String, SourcingReplyCache.Entry>>() {}.type

    override fun load(): Map<String, SourcingReplyCache.Entry> {
        val json = prefs.getString(KEY, null) ?: return emptyMap()
        // A cache that fails to read must be an empty cache, never a crash.
        return runCatching { gson.fromJson<Map<String, SourcingReplyCache.Entry>>(json, type) }
            .onFailure { Log.w(TAG, "could not read cache: ${it.message}") }
            .getOrNull() ?: emptyMap()
    }

    override fun save(entries: Map<String, SourcingReplyCache.Entry>) {
        runCatching { prefs.edit { putString(KEY, gson.toJson(entries, type)) } }
            .onFailure { Log.w(TAG, "could not write cache: ${it.message}") }
    }

    private companion object {
        const val KEY = "replies"
        const val TAG = "SourcingCache"
    }
}
