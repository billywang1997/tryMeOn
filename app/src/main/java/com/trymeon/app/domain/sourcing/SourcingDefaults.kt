package com.trymeon.app.domain.sourcing

/**
 * Freight and agent presets for the sourcing feature.
 *
 * The Taobao lines below carry that platform's own published rate card for
 * Australia — first weight and each additional step are per 0.5 kg, and a part
 * step bills as a whole one. The forwarder entries are deliberately generic
 * profiles instead: forwarder pricing changes every few months and varies per
 * warehouse, so a stale number printed beside a real company's name is worse
 * than an obviously editable default.
 *
 * Rates recorded 2026-08-28. Freight is the largest line in most quotes, so
 * these are the numbers to re-check first when a quote looks wrong.
 */
object SourcingDefaults {

    /** Taobao bills in 0.5 kg steps. */
    private const val HALF_KG = 500

    /**
     * Taobao's own consolidation, air, collected from a pickup point.
     *
     * The apparel promotion that ships free over ¥249 to Australia applies to
     * this route — which is why an order that looks marginal on freight often
     * is not, and why the app nudges toward the threshold.
     */
    val officialAirPickup = ShippingLine(
        id = "taobao-air-pickup",
        name = "Taobao air · pickup",
        firstWeightPriceCny = 63.0,
        firstWeightGrams = HALF_KG,
        additionalPriceCny = 53.0,
        additionalStepGrams = HALF_KG,
        volumetricDivisor = 6000,
        estimatedDays = 7..15,
        note = "Only listings that support Taobao direct shipping. Apparel free over ¥249. Max 20 kg.",
        route = ShippingRoute.OFFICIAL_DIRECT,
        weightPolicy = ChargeableWeightPolicy.VOLUMETRIC_OVER_3X,
        roundUpToGrams = HALF_KG,
        freeOverCny = 249.0
    )

    /** Same line, delivered to the door instead of a pickup point. */
    val officialAirHome = officialAirPickup.copy(
        id = "taobao-air-home",
        name = "Taobao air · home delivery",
        firstWeightPriceCny = 66.0,
        additionalPriceCny = 56.0
    )

    /**
     * Taobao sea freight. The additional step is ¥7 against air's ¥53, so this
     * is where a heavy or bulky order stops being absurd — at 25-45 days.
     */
    val officialSea = ShippingLine(
        id = "taobao-sea",
        name = "Taobao sea",
        firstWeightPriceCny = 66.0,
        firstWeightGrams = HALF_KG,
        additionalPriceCny = 7.0,
        additionalStepGrams = HALF_KG,
        volumetricDivisor = 6000,
        estimatedDays = 25..45,
        note = "Cheap per extra kilo. Over 20 kg adds a ¥150 handling fee.",
        route = ShippingRoute.OFFICIAL_DIRECT,
        weightPolicy = ChargeableWeightPolicy.VOLUMETRIC_OVER_3X,
        roundUpToGrams = HALF_KG,
        freeOverCny = 249.0
    )

    /**
     * Generic forwarder profiles. Unlike the Taobao lines these are placeholders
     * to be corrected against a real quote — and note they bill on the industry
     * max(actual, volumetric) rule, which is harsher than Taobao's 3:1.
     */
    val forwarderAir = ShippingLine(
        id = "forwarder-air",
        name = "Forwarder air",
        firstWeightPriceCny = 60.0,
        firstWeightGrams = HALF_KG,
        additionalPriceCny = 12.0,
        additionalStepGrams = 100,
        volumetricDivisor = 6000,
        estimatedDays = 8..16,
        note = "Placeholder rate — replace with your agent's table.",
        route = ShippingRoute.FORWARDER,
        weightPolicy = ChargeableWeightPolicy.GREATER_OF
    )

    val forwarderSea = forwarderAir.copy(
        id = "forwarder-sea",
        name = "Forwarder sea",
        firstWeightPriceCny = 80.0,
        firstWeightGrams = 1000,
        additionalPriceCny = 6.0,
        additionalStepGrams = HALF_KG,
        estimatedDays = 35..60
    )

    /** Taobao's own route first: for a qualifying listing it is usually the answer. */
    val lines = listOf(officialAirPickup, officialAirHome, officialSea, forwarderAir, forwarderSea)

    /** No forwarder in the loop means no service or payment fee to add. */
    val noAgent = DaigouAgent(
        id = "none",
        name = "Taobao direct",
        serviceFeePercent = 0.0,
        minServiceFeeCny = 0.0,
        paymentFeePercent = 0.0
    )

    val agents = listOf(
        noAgent,
        DaigouAgent("low-fee", "Low-fee forwarder", serviceFeePercent = 0.0, paymentFeePercent = 3.0),
        DaigouAgent("standard", "Standard agent", serviceFeePercent = 5.0, minServiceFeeCny = 10.0, paymentFeePercent = 3.0),
        DaigouAgent("full-service", "Full-service agent", serviceFeePercent = 8.0, minServiceFeeCny = 20.0, paymentFeePercent = 3.0)
    )

    /** Kept for call sites that still name the old official-direct preset. */
    val officialDirect get() = officialAirPickup

    val defaultLine get() = forwarderAir
    val defaultAgent get() = agents[2]

    /**
     * Last-resort CNY→AUD, used only when a live rate has never been fetched on
     * this device. A stale rate skews every quote at once, so the UI must label
     * it — see FxPolicy.label. Reference value taken 2026-08-28 (ECB 0.20696).
     */
    const val FALLBACK_CNY_TO_AUD = 0.207

    /**
     * Default cost of settling a yuan bill from Australia, as a percentage.
     *
     * Roughly Alipay's 2.5% overseas-card service fee plus its spread over the
     * mid-market rate. Paying a card directly on Taobao runs higher again — the
     * issuer's foreign transaction fee is typically 2-3% here, on top of its own
     * spread. Around 25 Australian cards charge no foreign transaction fee at
     * all, which is why this is offered as a choice rather than baked in.
     */
    const val DEFAULT_CARD_SETTLEMENT_PERCENT = 3.0

    /** Options offered in the UI: a no-FX-fee card, Alipay, and a typical AU card. */
    val cardSettlementOptions = listOf(0.0, 2.5, 3.0, 5.0)
}
