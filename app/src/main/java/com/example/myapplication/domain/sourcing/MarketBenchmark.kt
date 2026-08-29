package com.example.myapplication.domain.sourcing

import kotlin.math.roundToInt

/**
 * What a comparable item costs in Australia, and how far below it a landed
 * price sits.
 *
 * This is the comparison that makes a landed price mean something. "A$27
 * delivered" is a number; "A$27 against the A$85 these normally cost here" is
 * an argument. It is deliberately a comparison against *similar* items rather
 * than the identical one — matching an exact product across two markets is not
 * possible, and pretending otherwise would be the more misleading claim.
 */
data class MarketBenchmark(
    /** Typical local price in AUD. */
    val typicalAud: Double,
    /** How many comparable listings it was drawn from. */
    val sampleSize: Int
) {
    /** Percentage below the local typical price, or null when not cheaper. */
    fun savingPercentAgainst(landedAud: Double): Int? {
        if (typicalAud <= 0.0 || landedAud <= 0.0 || landedAud >= typicalAud) return null
        return ((1 - landedAud / typicalAud) * 100).roundToInt().takeIf { it >= MIN_MEANINGFUL_SAVING }
    }

    companion object {
        /** Below this the claim is noise, and noise on a price is worse than silence. */
        const val MIN_MEANINGFUL_SAVING = 10

        /** Fewer comparable listings than this is not a market, it is an anecdote. */
        const val MIN_SAMPLE = 4

        /**
         * Build a benchmark from local listing prices.
         *
         * The median, not the mean: a shopping search for "linen blazer" also
         * returns a five dollar hanger and a nine hundred dollar designer
         * piece, and either one moves a mean enough to invent or erase a
         * saving. Extremes are dropped before that, because with a small
         * sample the median alone still leans.
         */
        fun from(prices: List<Double>): MarketBenchmark? {
            val valid = prices.filter { it > 0 && it.isFinite() }.sorted()
            if (valid.size < MIN_SAMPLE) return null

            // Trim the tails once the sample is big enough to afford it.
            val trim = if (valid.size >= 8) valid.size / 8 else 0
            val core = valid.subList(trim, valid.size - trim)

            val median = if (core.size % 2 == 1) core[core.size / 2]
                         else (core[core.size / 2 - 1] + core[core.size / 2]) / 2.0

            return MarketBenchmark(typicalAud = median, sampleSize = valid.size)
        }
    }
}
