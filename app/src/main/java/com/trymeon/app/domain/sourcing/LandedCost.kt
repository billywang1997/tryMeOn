package com.trymeon.app.domain.sourcing

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What a Taobao/1688 listing actually costs once it reaches an Australian door.
 *
 * A ¥89 listing is not A$19. Between the listing and the doorstep sit domestic
 * Chinese shipping, the forwarding agent's service and payment fees, air freight
 * billed on *volumetric* weight rather than actual weight, and GST. Showing the
 * landed number is the whole point of this feature — the sticker price is the
 * one thing every other app already shows.
 *
 * Every rate here is an input, not a constant: freight tables and agent fees
 * change constantly and the caller is expected to keep them current.
 */

/**
 * Which route the parcel takes.
 *
 * Taobao now ships some listings to Australia itself, with apparel free over a
 * spend threshold. That path has no forwarder and often no freight at all — but
 * only a small minority of listings support it, and nothing on a listing page
 * tells an overseas buyer which minority they are looking at. Modelling both
 * routes is what lets the app answer the question the seller will not.
 */
enum class ShippingRoute {
    /** Taobao's own consolidated/direct shipping. No agent, no service fee. */
    OFFICIAL_DIRECT,

    /** Buy to a forwarder's China warehouse, then reship. Works for any listing. */
    FORWARDER
}

/** How an air/sea line prices a parcel. Rates are in CNY. */
data class ShippingLine(
    val id: String,
    val name: String,
    /** Price covering everything up to [firstWeightGrams]. */
    val firstWeightPriceCny: Double,
    val firstWeightGrams: Int,
    /** Price per additional step beyond the first weight. */
    val additionalPriceCny: Double,
    val additionalStepGrams: Int,
    /**
     * Divisor for L×W×H in cm. 6000 is the usual air-freight convention; some
     * express lines use 5000, which makes bulky-but-light clothing far dearer.
     */
    val volumetricDivisor: Int = 6000,
    val estimatedDays: IntRange = 7..15,
    val note: String = "",
    val route: ShippingRoute = ShippingRoute.FORWARDER,
    val weightPolicy: ChargeableWeightPolicy = ChargeableWeightPolicy.GREATER_OF,
    /**
     * Billing granularity. Taobao bills in 0.5 kg steps and rounds a part step
     * up, so a 1250 g parcel is charged as 1.5 kg.
     */
    val roundUpToGrams: Int = 1,
    /**
     * Goods subtotal (CNY) at or above which this line ships free. Taobao's
     * apparel promotion to Australia works this way; forwarder lines do not.
     */
    val freeOverCny: Double? = null
)

/** A forwarding agent (代购/转运). Kept generic — fees are the agent's, not ours. */
data class DaigouAgent(
    val id: String,
    val name: String,
    /** Service fee as a percentage of the goods subtotal. */
    val serviceFeePercent: Double,
    /** Floor on the service fee, in CNY. */
    val minServiceFeeCny: Double = 0.0,
    /** Payment-processing surcharge, percentage of the amount paid. */
    val paymentFeePercent: Double = 0.0,
    /**
     * Affiliate deep-link template. `{url}` is replaced with the URL-encoded
     * product link. Deliberately per-agent: tying the whole feature to one
     * partner is how you lose the feature when that partner changes terms.
     */
    val affiliateTemplate: String = ""
)

/**
 * How a line decides which weight to bill.
 *
 * Taobao's own consolidation does NOT use the industry default. Its published
 * rule for Australia, New Zealand, Singapore, Malaysia, the US and Canada is
 * that volumetric weight only takes over once it exceeds actual weight by more
 * than 3:1 — far more forgiving than max(), and applying max() to that line
 * overstates the freight on exactly the light, bulky clothing this app is for.
 */
enum class ChargeableWeightPolicy {
    /** Industry default, used by forwarders: bill the larger of the two. */
    GREATER_OF,

    /** Taobao official: bill actual unless volumetric exceeds it by more than 3×. */
    VOLUMETRIC_OVER_3X
}

/** Parcel geometry. Dimensions drive volumetric weight, which usually wins for clothing. */
data class Parcel(
    val lengthCm: Double,
    val widthCm: Double,
    val heightCm: Double,
    val actualGrams: Int
) {
    fun volumetricGrams(divisor: Int): Int =
        if (divisor <= 0) 0
        else ((lengthCm * widthCm * heightCm) / divisor * 1000).roundToInt()

    /**
     * Weight before rounding, under [policy]. See [ChargeableWeightPolicy] —
     * picking the wrong one here is a silent double-digit error on every quote.
     */
    fun chargeableGrams(
        divisor: Int,
        policy: ChargeableWeightPolicy = ChargeableWeightPolicy.GREATER_OF
    ): Int {
        val volumetric = volumetricGrams(divisor)
        return when (policy) {
            ChargeableWeightPolicy.GREATER_OF -> max(actualGrams, volumetric)
            ChargeableWeightPolicy.VOLUMETRIC_OVER_3X ->
                if (actualGrams > 0 && volumetric > actualGrams * 3) volumetric else actualGrams
        }
    }
}

/**
 * How GST lands on the consignment.
 *
 * Australia's low-value regime (since July 2018) makes the overseas supplier or
 * platform collect 10% at checkout for consignments valued at A$1000 or less;
 * nothing is charged at the border. Above A$1000 it flips: duty and GST are
 * assessed on arrival, plus an import processing charge.
 */
enum class GstTreatment {
    /** Charge 10% on goods + shipping, the way a registered platform would. */
    LOW_VALUE_COLLECTED,

    /** Many forwarders are not registered and collect nothing. Honest to model. */
    NOT_COLLECTED,

    /** Over the A$1000 threshold: duty first, then GST on the whole taxable value. */
    BORDER_ASSESSED
}

data class LandedCostInput(
    val itemPriceCny: Double,
    val quantity: Int = 1,
    /** Seller-to-warehouse shipping inside China. Often free, often not. */
    val domesticShippingCny: Double = 0.0,
    val parcel: Parcel,
    val agent: DaigouAgent,
    val line: ShippingLine,
    /** AUD per 1 CNY. */
    val cnyToAud: Double,
    val gst: GstTreatment = GstTreatment.LOW_VALUE_COLLECTED,
    /** Apparel into Australia is typically 5%, and only above the threshold. */
    val dutyPercent: Double = 5.0,
    /** Border processing charge for above-threshold consignments, in AUD. */
    val importProcessingAud: Double = 0.0,
    /**
     * What settling the CNY bill actually costs on top of the mid-market rate.
     *
     * Every other figure here converts at the ECB reference rate, which nobody
     * is ever offered. Paying through Alipay with an overseas card adds a
     * service fee plus Alipay's own spread; paying a card directly adds the
     * issuer's foreign transaction fee — 2-3% is typical in Australia, though
     * a number of cards charge nothing. Leaving it out understates every quote,
     * so it is a visible, adjustable line rather than a silent omission.
     */
    val cardSettlementPercent: Double = SourcingDefaults.DEFAULT_CARD_SETTLEMENT_PERCENT
)

/** One line of the breakdown. AUD is what the user sees; CNY is kept for auditability. */
data class CostLine(val label: String, val amountAud: Double, val amountCny: Double? = null)

data class LandedCost(
    val lines: List<CostLine>,
    val totalAud: Double,
    val chargeableGrams: Int,
    val volumetricGrams: Int,
    val actualGrams: Int,
    val estimatedDays: IntRange
) {
    /** True when the parcel is billed on its size rather than what it weighs. */
    val billedOnVolume: Boolean get() = chargeableGrams > actualGrams

    /** What the listing price alone would have suggested — the number to contrast against. */
    val stickerAud: Double get() = lines.firstOrNull()?.amountAud ?: 0.0

    /** How many times the sticker price the real cost is. */
    val multiplier: Double get() = if (stickerAud <= 0.0) 0.0 else totalAud / stickerAud
}

/** How much extra weight escapes volumetric billing, and what that is worth. */
data class VolumetricEscape(
    val extraGrams: Int,
    val savingCny: Double,
    val savingAud: Double
)

object LandedCostCalculator {

    /** Consignments valued above this (AUD) are assessed at the border instead. */
    const val LOW_VALUE_THRESHOLD_AUD = 1000.0

    private const val GST_RATE = 0.10

    fun calculate(input: LandedCostInput): LandedCost {
        val fx = input.cnyToAud
        fun aud(cny: Double) = cny * fx

        val goodsCny = input.itemPriceCny * input.quantity
        val subtotalCny = goodsCny + input.domesticShippingCny

        val serviceFeeCny =
            max(subtotalCny * input.agent.serviceFeePercent / 100.0, input.agent.minServiceFeeCny)
        val freightCny = freightCny(input)
        // The payment surcharge applies to everything the agent actually charges.
        val paymentFeeCny =
            (subtotalCny + serviceFeeCny + freightCny) * input.agent.paymentFeePercent / 100.0

        val lines = mutableListOf(
            CostLine("Item ×${input.quantity}", aud(goodsCny), goodsCny)
        )
        if (input.domesticShippingCny > 0) {
            lines += CostLine("China domestic shipping", aud(input.domesticShippingCny), input.domesticShippingCny)
        }
        if (serviceFeeCny > 0) {
            lines += CostLine("${input.agent.name} service fee", aud(serviceFeeCny), serviceFeeCny)
        }
        if (freightCny > 0) {
            lines += CostLine("Freight — ${input.line.name}", aud(freightCny), freightCny)
        } else {
            lines += CostLine("Freight — ${input.line.name} (free)", 0.0, 0.0)
        }
        if (paymentFeeCny > 0) {
            lines += CostLine("Payment fee", aud(paymentFeeCny), paymentFeeCny)
        }

        // GST and duty are assessed in AUD, so convert before applying them.
        val goodsAud = aud(goodsCny + input.domesticShippingCny)
        val freightAud = aud(freightCny)

        // The threshold is on the customs value of the goods, not the total paid.
        val treatment =
            if (input.gst == GstTreatment.LOW_VALUE_COLLECTED && goodsAud > LOW_VALUE_THRESHOLD_AUD)
                GstTreatment.BORDER_ASSESSED
            else input.gst

        when (treatment) {
            GstTreatment.LOW_VALUE_COLLECTED -> {
                lines += CostLine("GST (10%)", (goodsAud + freightAud) * GST_RATE)
            }
            GstTreatment.BORDER_ASSESSED -> {
                val duty = goodsAud * input.dutyPercent / 100.0
                if (duty > 0) lines += CostLine("Customs duty (${fmt(input.dutyPercent)}%)", duty)
                // GST base above the threshold includes the duty and the freight.
                lines += CostLine("GST (10%)", (goodsAud + freightAud + duty) * GST_RATE)
                if (input.importProcessingAud > 0) {
                    lines += CostLine("Import processing charge", input.importProcessingAud)
                }
            }
            GstTreatment.NOT_COLLECTED -> Unit
        }

        // Applies to what is actually billed in yuan. An AUD-denominated border
        // charge is paid at home and never crosses a currency.
        val cnyBilled = lines.sumOf { it.amountCny ?: 0.0 }
        if (input.cardSettlementPercent > 0 && cnyBilled > 0) {
            lines += CostLine(
                "Card FX & fees (${fmt(input.cardSettlementPercent)}%)",
                aud(cnyBilled) * input.cardSettlementPercent / 100.0
            )
        }

        val divisor = input.line.volumetricDivisor
        return LandedCost(
            lines = lines,
            totalAud = lines.sumOf { it.amountAud },
            chargeableGrams = chargeableGrams(input),
            volumetricGrams = input.parcel.volumetricGrams(divisor),
            actualGrams = input.parcel.actualGrams,
            estimatedDays = input.line.estimatedDays
        )
    }

    /**
     * How much more must go in the basket before freight drops to zero, or null
     * when the line has no threshold or it is already met. This is the cheapest
     * genuinely useful nudge in the whole feature.
     */
    fun addToFreeShippingCny(input: LandedCostInput): Double? {
        val threshold = input.line.freeOverCny ?: return null
        val subtotal = input.itemPriceCny * input.quantity + input.domesticShippingCny
        val gap = threshold - subtotal
        return if (gap > 0) gap else null
    }

    /**
     * On a 3:1 line, how little extra actual weight would drop the parcel off
     * volumetric billing — and what that saves.
     *
     * The rule produces a cliff that reads backwards: a 400 g garment in a
     * 30×25×10 box bills at 1.5 kg, while the same box at 417 g bills at 0.5 kg.
     * Seventeen grams — one more small item in the consolidation — is worth ¥106.
     * Nothing on Taobao will ever tell a buyer this.
     */
    fun volumetricEscape(input: LandedCostInput): VolumetricEscape? {
        val line = input.line
        if (line.weightPolicy != ChargeableWeightPolicy.VOLUMETRIC_OVER_3X) return null

        val volumetric = input.parcel.volumetricGrams(line.volumetricDivisor)
        val actual = input.parcel.actualGrams
        if (actual <= 0 || volumetric <= actual * 3) return null

        // "Not greater than 3:1" bills actual, so the target is the first weight
        // at which volumetric stops exceeding three times it.
        val minActual = ceil(volumetric / 3.0).toInt()
        val extraGrams = minActual - actual
        if (extraGrams <= 0) return null

        val cheaper = input.copy(parcel = input.parcel.copy(actualGrams = minActual))
        val saving = freightCny(input) - freightCny(cheaper)
        if (saving <= 0.0) return null

        return VolumetricEscape(extraGrams, saving, saving * input.cnyToAud)
    }

    /** Billable weight for [input]: the line's policy first, then its rounding step. */
    fun chargeableGrams(input: LandedCostInput): Int {
        val line = input.line
        val raw = input.parcel.chargeableGrams(line.volumetricDivisor, line.weightPolicy)
        val step = line.roundUpToGrams
        if (step <= 1) return raw
        // A part step is a whole step: 1250 g on a 0.5 kg table bills as 1.5 kg.
        return ceil(raw.toDouble() / step).toInt() * step
    }

    /** First-weight plus whole additional steps — freight tables round up, never pro-rate. */
    private fun freightCny(input: LandedCostInput): Double {
        val line = input.line
        val subtotal = input.itemPriceCny * input.quantity + input.domesticShippingCny
        line.freeOverCny?.let { if (subtotal >= it) return 0.0 }
        val chargeable = chargeableGrams(input)
        if (chargeable <= line.firstWeightGrams) return line.firstWeightPriceCny
        if (line.additionalStepGrams <= 0) return line.firstWeightPriceCny

        val extra = chargeable - line.firstWeightGrams
        val steps = ceil(extra.toDouble() / line.additionalStepGrams).toInt()
        return line.firstWeightPriceCny + steps * line.additionalPriceCny
    }

    private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}
