package com.trymeon.app.domain.sourcing

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
    val sampleSize: Int,
    /** The middle half of the sample: the range worth quoting when the median is not. */
    val lowAud: Double = typicalAud,
    val highAud: Double = typicalAud
) {
    /**
     * Whether a single "usual price" is a fair summary of this sample.
     *
     * Measured against real results: jeans come back at an interquartile spread
     * of 0.42 of the median and a median means something. Leather jackets come
     * back at 1.5, and blazers once at 2.1, because those searches return two
     * different markets at once — fast-fashion PU beside designer leather, at
     * A$40 and at A$1560. Calling either of those "about A$310" and computing a
     * percentage off it is a claim the prices do not support.
     */
    val isCoherent: Boolean
        get() = typicalAud > 0 && (highAud - lowAud) / typicalAud <= MAX_SPREAD

    /**
     * Percentage below the local typical price.
     *
     * Null when the item is not cheaper, when the saving is too small to be
     * worth a line, or when the sample is too spread out for a single price to
     * summarise — in that last case the range is the honest thing to show, and
     * the caller has it.
     */
    fun savingPercentAgainst(landedAud: Double): Int? {
        if (!isCoherent) return null
        if (typicalAud <= 0.0 || landedAud <= 0.0 || landedAud >= typicalAud) return null
        return ((1 - landedAud / typicalAud) * 100).roundToInt().takeIf { it >= MIN_MEANINGFUL_SAVING }
    }

    companion object {
        /** Below this the claim is noise, and noise on a price is worse than silence. */
        const val MIN_MEANINGFUL_SAVING = 10

        /** Fewer comparable listings than this is not a market, it is an anecdote. */
        const val MIN_SAMPLE = 4

        /**
         * How far the middle half may spread, as a multiple of the median,
         * before a single price stops describing the sample. Drawn from real
         * searches: coherent ones sit between 0.4 and 0.9, and the ones that
         * are two markets in a trench coat sit above 1.2.
         */
        const val MAX_SPREAD = 1.0

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

            fun medianOf(v: List<Double>) = if (v.size % 2 == 1) v[v.size / 2]
                else (v[v.size / 2 - 1] + v[v.size / 2]) / 2.0

            // The median is taken from the trimmed core, which is the better
            // estimate. The quartiles are taken from the whole sample, because
            // the question they answer is about the market the shopper is being
            // told about — "18 listings" — and trimming the ends of a bimodal
            // set hides exactly the thing worth noticing.
            val median = medianOf(core)
            val low = medianOf(valid.subList(0, valid.size / 2))
            val high = medianOf(valid.subList((valid.size + 1) / 2, valid.size))

            return MarketBenchmark(
                typicalAud = median,
                sampleSize = valid.size,
                lowAud = low,
                highAud = high
            )
        }
    }
}
