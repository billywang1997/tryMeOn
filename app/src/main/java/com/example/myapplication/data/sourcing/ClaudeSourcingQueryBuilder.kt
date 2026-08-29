package com.example.myapplication.data.sourcing

import android.util.Log
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.domain.model.ClothingCategory

/**
 * Default [SourcingQueryBuilder], backed by the same model the rest of the app
 * uses for styling.
 *
 * The prompt asks for seller vocabulary rather than a translation on purpose. A
 * literal rendering of "cropped linen blazer" returns almost nothing on Taobao;
 * what sellers actually title the listing is closer to "亚麻小西装外套 短款".
 * Getting that gap right is the difference between this feature working and it
 * returning an empty grid.
 */
class ClaudeSourcingQueryBuilder(
    private val service: ClaudeApiService,
    private val apiKey: String,
    private val quantity: Int = 1
) : SourcingQueryBuilder {

    override suspend fun build(
        englishDescription: String,
        gender: String,
        categoryHint: ClothingCategory?
    ): Result<SourcingQuery> {
        if (englishDescription.isBlank()) {
            return Result.failure(IllegalArgumentException("Describe the item first"))
        }
        return runCatching {
            val raw = service.sourcingQuery(apiKey, englishDescription, gender, categoryHint?.name.orEmpty())
            Log.d(TAG, "raw sourcing reply: ${raw.take(300)}")
            SourcingQueryParser.parse(raw, categoryHint, quantity)
                ?: error("Could not turn that into a Taobao search")
        }
    }

    private companion object { const val TAG = "SourcingQuery" }
}
