package com.example.myapplication.data.sourcing

import com.example.myapplication.AppSettings
import com.example.myapplication.data.remote.FxRate
import com.example.myapplication.data.remote.FxRateService
import com.example.myapplication.domain.sourcing.SourcingDefaults
import java.util.concurrent.TimeUnit

/**
 * Keeps one CNY→AUD rate for the whole sourcing feature, with a last-known-good
 * value that survives being offline.
 *
 * The degradation order matters: a fresh fetch, then the last rate we actually
 * saw, and only then the compiled-in constant. Falling straight back to the
 * constant would keep quoting confidently off a number that was last true
 * whenever the app was built.
 */
class FxRateRepository(private val settings: AppSettings) {

    suspend fun current(now: Long = System.currentTimeMillis()): FxRate {
        val cached = stored()
        if (cached != null && !FxPolicy.shouldRefetch(cached.fetchedAtMillis, now)) return cached

        val fetched = FxRateService.fetch()
        if (fetched != null) {
            settings.cnyToAudRate = fetched.rate.toFloat()
            settings.cnyToAudFetchedAt = fetched.fetchedAtMillis
            settings.cnyToAudSource = fetched.source
            return fetched
        }
        return cached ?: FxRate(
            rate = SourcingDefaults.FALLBACK_CNY_TO_AUD,
            fetchedAtMillis = 0L,
            source = "indicative",
            isFallback = true
        )
    }

    private fun stored(): FxRate? {
        val rate = settings.cnyToAudRate.toDouble()
        val at = settings.cnyToAudFetchedAt
        if (rate <= 0.0 || at <= 0L) return null
        return FxRate(rate, at, settings.cnyToAudSource.ifBlank { "cached" })
    }
}

/** Refresh and labelling rules, kept pure so they can be tested without a clock or a network. */
object FxPolicy {

    /** Reference rates move once a business day; refetching more often buys nothing. */
    val FRESH_WINDOW_MS: Long = TimeUnit.HOURS.toMillis(12)

    /** Past this the rate still beats the constant, but the UI must say it is old. */
    val STALE_AFTER_MS: Long = TimeUnit.DAYS.toMillis(4)

    fun shouldRefetch(fetchedAtMillis: Long, now: Long): Boolean {
        if (fetchedAtMillis <= 0L) return true
        val age = now - fetchedAtMillis
        // A timestamp from the future means a clock change, not a fresh rate.
        return age < 0 || age >= FRESH_WINDOW_MS
    }

    fun isStale(rate: FxRate, now: Long): Boolean =
        rate.isFallback || rate.fetchedAtMillis <= 0L || (now - rate.fetchedAtMillis) >= STALE_AFTER_MS

    /** Short label shown next to any converted price, e.g. "¥1 = A$0.2070 · ECB today". */
    fun label(rate: FxRate, now: Long): String {
        val figure = "¥1 = A$${"%.4f".format(rate.rate)}"
        if (rate.isFallback) return "$figure · indicative rate"

        val ageMs = (now - rate.fetchedAtMillis).coerceAtLeast(0L)
        val days = TimeUnit.MILLISECONDS.toDays(ageMs)
        val age = when {
            days <= 0L -> "today"
            days == 1L -> "yesterday"
            else -> "$days days old"
        }
        return "$figure · ${rate.source} $age"
    }
}
