package com.example.myapplication

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

    var onboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) = prefs.edit { putBoolean("onboarding_complete", value) }

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
    }
}
