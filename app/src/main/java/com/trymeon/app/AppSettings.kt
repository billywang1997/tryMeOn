package com.trymeon.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var claudeApiKey: String
        get() = prefs.getString("claude_api_key", OPENAI_KEY)?.takeIf { it.isNotBlank() } ?: OPENAI_KEY
        set(value) = prefs.edit { putString("claude_api_key", value) }

    var fashnApiKey: String
        get() = prefs.getString("fashn_api_key", FASHN_KEY)?.takeIf { it.isNotBlank() } ?: FASHN_KEY
        set(value) = prefs.edit { putString("fashn_api_key", value) }

    var ebayClientId: String
        get() = prefs.getString("ebay_client_id", EBAY_CLIENT_ID)?.takeIf { it.isNotBlank() } ?: EBAY_CLIENT_ID
        set(value) = prefs.edit { putString("ebay_client_id", value) }

    var ebayClientSecret: String
        get() = prefs.getString("ebay_client_secret", EBAY_CLIENT_SECRET)?.takeIf { it.isNotBlank() } ?: EBAY_CLIENT_SECRET
        set(value) = prefs.edit { putString("ebay_client_secret", value) }

    // eBay Partner Network campaign ID — register at partnernetwork.ebay.com
    var ebayAffiliateCampaignId: String
        get() = prefs.getString("ebay_affiliate_campaign_id", EBAY_AFFILIATE_CAMPAIGN_ID)?.takeIf { it.isNotBlank() } ?: EBAY_AFFILIATE_CAMPAIGN_ID
        set(value) = prefs.edit { putString("ebay_affiliate_campaign_id", value) }

    // Single RapidAPI key shared across Taobao, SHEIN, Vinted, AliExpress
    var rapidApiKey: String
        get() = prefs.getString("rapid_api_key", RAPID_API_KEY)?.takeIf { it.isNotBlank() } ?: RAPID_API_KEY
        set(value) = prefs.edit { putString("rapid_api_key", value) }

    // SerpAPI — free 100/mo Google Shopping aggregator
    var serpApiKey: String
        get() = prefs.getString("serp_api_key", SERP_API_KEY)?.takeIf { it.isNotBlank() } ?: SERP_API_KEY
        set(value) = prefs.edit { putString("serp_api_key", value) }

    // ScraperAPI — free 1000 credits/mo, Amazon structured search
    var scraperApiKey: String
        get() = prefs.getString("scraper_api_key", SCRAPER_API_KEY)?.takeIf { it.isNotBlank() } ?: SCRAPER_API_KEY
        set(value) = prefs.edit { putString("scraper_api_key", value) }

    // Skimlinks publisher ID — auto-affiliates any retailer URL
    var skimlinksId: String
        get() = prefs.getString("skimlinks_id", SKIMLINKS_ID)?.takeIf { it.isNotBlank() } ?: SKIMLINKS_ID
        set(value) = prefs.edit { putString("skimlinks_id", value) }

    // Sovrn Commerce site ID — Skimlinks alternative for A/B comparison
    var sovrnSiteId: String
        get() = prefs.getString("sovrn_site_id", SOVRN_SITE_ID)?.takeIf { it.isNotBlank() } ?: SOVRN_SITE_ID
        set(value) = prefs.edit { putString("sovrn_site_id", value) }

    // Forwarding-agent deep links, as `id|name|template|code` joined by `;;`.
    var daigouProviders: String
        get() = prefs.getString("daigou_providers", DAIGOU_PROVIDERS)?.takeIf { it.isNotBlank() } ?: DAIGOU_PROVIDERS
        set(value) = prefs.edit { putString("daigou_providers", value) }

    var preferredDaigouId: String
        get() = prefs.getString("preferred_daigou_id", "") ?: ""
        set(value) = prefs.edit { putString("preferred_daigou_id", value) }

    var unsplashAccessKey: String
        get() = prefs.getString("unsplash_access_key", UNSPLASH_KEY) ?: UNSPLASH_KEY
        set(value) = prefs.edit { putString("unsplash_access_key", value) }

    var amazonAccessKey: String
        get() = prefs.getString("amazon_access_key", "") ?: ""
        set(value) = prefs.edit { putString("amazon_access_key", value) }

    var amazonSecretKey: String
        get() = prefs.getString("amazon_secret_key", "") ?: ""
        set(value) = prefs.edit { putString("amazon_secret_key", value) }

    var amazonAssociateTag: String
        get() = prefs.getString("amazon_associate_tag", "") ?: ""
        set(value) = prefs.edit { putString("amazon_associate_tag", value) }

    // Google Sign-In web client ID (from Firebase console → Project Settings → Web API key)
    var googleWebClientId: String
        get() = prefs.getString("google_web_client_id", "") ?: ""
        set(value) = prefs.edit { putString("google_web_client_id", value) }

    // Google Custom Search — for wardrobe essentials product images
    var googleSearchApiKey: String
        get() = prefs.getString("google_search_api_key", GOOGLE_SEARCH_KEY) ?: GOOGLE_SEARCH_KEY
        set(value) = prefs.edit { putString("google_search_api_key", value) }

    var googleSearchEngineId: String
        get() = prefs.getString("google_search_engine_id", GOOGLE_SEARCH_CX) ?: GOOGLE_SEARCH_CX
        set(value) = prefs.edit { putString("google_search_engine_id", value) }

    // The generated full-body portrait every try-on is dressed from. Persisted
    // rather than cached: it costs a paid image generation, and rebuilding it
    // each session would also return a slightly different person each time.
    var virtualModelPath: String
        get() = prefs.getString("virtual_model_path", "") ?: ""
        set(value) = prefs.edit { putString("virtual_model_path", value) }

    /** Inputs the stored portrait was built from; a change invalidates it. */
    var virtualModelSignature: String
        get() = prefs.getString("virtual_model_signature", "") ?: ""
        set(value) = prefs.edit { putString("virtual_model_signature", value) }

    var virtualModelCreatedAt: Long
        get() = prefs.getLong("virtual_model_created_at", 0L)
        set(value) = prefs.edit { putLong("virtual_model_created_at", value) }

    // Last CNY→AUD rate we actually saw, so a landed-cost quote made offline
    // falls back to a real rate rather than to the compiled-in constant.
    var cnyToAudRate: Float
        get() = prefs.getFloat("cny_to_aud_rate", 0f)
        set(value) = prefs.edit { putFloat("cny_to_aud_rate", value) }

    var cnyToAudFetchedAt: Long
        get() = prefs.getLong("cny_to_aud_fetched_at", 0L)
        set(value) = prefs.edit { putLong("cny_to_aud_fetched_at", value) }

    var cnyToAudSource: String
        get() = prefs.getString("cny_to_aud_source", "") ?: ""
        set(value) = prefs.edit { putString("cny_to_aud_source", value) }

    // Purchase token whose server-side entitlement has already been granted.
    // Stops restorePurchases() from re-calling verifyPurchase on every launch.
    var entitlementSyncedFor: String
        get() = prefs.getString("entitlement_synced_for", "") ?: ""
        set(value) = prefs.edit { putString("entitlement_synced_for", value) }

    // Premium one-shot unlock for Closet Audit. Once true, audit screen skips paywall.
    var auditUnlocked: Boolean
        get() = prefs.getBoolean("audit_unlocked", false)
        set(value) = prefs.edit { putBoolean("audit_unlocked", value) }

    // Notification preferences (defaults: ON)
    var notifyPriceDrops: Boolean
        get() = prefs.getBoolean("notify_price_drops", true)
        set(value) = prefs.edit { putBoolean("notify_price_drops", value) }

    var notifyStreakReminder: Boolean
        get() = prefs.getBoolean("notify_streak_reminder", true)
        set(value) = prefs.edit { putBoolean("notify_streak_reminder", value) }

    var onboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) = prefs.edit { putBoolean("onboarding_complete", value) }

    // How recommended prices should sit against Australian retail. Read by the
    // listing ranker and every stylist prompt, so all shop strips agree on it.
    var priceExpectation: com.trymeon.app.domain.sourcing.PriceExpectation
        get() = com.trymeon.app.domain.sourcing.PriceExpectation.fromName(
            prefs.getString("price_expectation", null)
        )
        set(value) = prefs.edit { putString("price_expectation", value.name) }

    var styleKeywords: Set<String>
        get() = prefs.getStringSet("style_keywords", emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet("style_keywords", value) }

    companion object {
        // Defaults are injected from local.properties via BuildConfig.
        // See app/build.gradle.kts and local.properties.example.
        private val OPENAI_KEY = BuildConfig.CLAUDE_API_KEY
        private val FASHN_KEY = BuildConfig.FASHN_API_KEY
        private val UNSPLASH_KEY = BuildConfig.UNSPLASH_ACCESS_KEY
        private val GOOGLE_SEARCH_KEY = BuildConfig.GOOGLE_SEARCH_KEY
        private val GOOGLE_SEARCH_CX = BuildConfig.GOOGLE_SEARCH_CX
        private val EBAY_CLIENT_ID = BuildConfig.EBAY_CLIENT_ID
        private val EBAY_CLIENT_SECRET = BuildConfig.EBAY_CLIENT_SECRET
        private val EBAY_AFFILIATE_CAMPAIGN_ID = BuildConfig.EBAY_AFFILIATE_CAMPAIGN_ID
        private val RAPID_API_KEY = BuildConfig.RAPID_API_KEY
        private val SERP_API_KEY = BuildConfig.SERP_API_KEY
        private val SCRAPER_API_KEY = BuildConfig.SCRAPER_API_KEY
        private val SKIMLINKS_ID = BuildConfig.SKIMLINKS_ID
        private val SOVRN_SITE_ID = BuildConfig.SOVRN_SITE_ID
        private val DAIGOU_PROVIDERS = BuildConfig.DAIGOU_PROVIDERS
    }
}
