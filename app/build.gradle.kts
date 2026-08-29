import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
fun secret(key: String): String = localProps.getProperty(key, "")

// Base URL of the Cloud Functions relay (see functions/). When set, the app
// sends every third-party API call through it and no upstream key needs to be
// — or should be — compiled into the APK.
val relayBaseUrl: String = secret("RELAY_BASE_URL")

// Upstream keys are only ever a debug convenience for calling APIs directly.
// A release build that has a relay must ship none of them: anything in
// BuildConfig is readable by anyone who unzips the APK.
fun upstreamSecret(key: String, isRelease: Boolean): String =
    if (isRelease && relayBaseUrl.isNotBlank()) "" else secret(key)

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "RELAY_BASE_URL", "\"$relayBaseUrl\"")

        // Affiliate/publisher IDs are not credentials — they identify us for
        // commission attribution and are meant to travel with the click.
        buildConfigField("String", "EBAY_AFFILIATE_CAMPAIGN_ID","\"${secret("EBAY_AFFILIATE_CAMPAIGN_ID")}\"")
        buildConfigField("String", "SKIMLINKS_ID",              "\"${secret("SKIMLINKS_ID")}\"")
        buildConfigField("String", "SOVRN_SITE_ID",             "\"${secret("SOVRN_SITE_ID")}\"")
        // Forwarding-agent deep links. See Daigou.parse for the format.
        buildConfigField("String", "DAIGOU_PROVIDERS",           "\"${secret("DAIGOU_PROVIDERS")}\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "CLAUDE_API_KEY",       "\"${upstreamSecret("CLAUDE_API_KEY", false)}\"")
            buildConfigField("String", "FASHN_API_KEY",        "\"${upstreamSecret("FASHN_API_KEY", false)}\"")
            buildConfigField("String", "UNSPLASH_ACCESS_KEY",  "\"${upstreamSecret("UNSPLASH_ACCESS_KEY", false)}\"")
            buildConfigField("String", "GOOGLE_SEARCH_KEY",    "\"${upstreamSecret("GOOGLE_SEARCH_KEY", false)}\"")
            buildConfigField("String", "GOOGLE_SEARCH_CX",     "\"${upstreamSecret("GOOGLE_SEARCH_CX", false)}\"")
            buildConfigField("String", "EBAY_CLIENT_ID",       "\"${upstreamSecret("EBAY_CLIENT_ID", false)}\"")
            buildConfigField("String", "EBAY_CLIENT_SECRET",   "\"${upstreamSecret("EBAY_CLIENT_SECRET", false)}\"")
            buildConfigField("String", "RAPID_API_KEY",        "\"${upstreamSecret("RAPID_API_KEY", false)}\"")
            buildConfigField("String", "SERP_API_KEY",         "\"${upstreamSecret("SERP_API_KEY", false)}\"")
            buildConfigField("String", "SCRAPER_API_KEY",      "\"${upstreamSecret("SCRAPER_API_KEY", false)}\"")
        }
        release {
            buildConfigField("String", "CLAUDE_API_KEY",       "\"${upstreamSecret("CLAUDE_API_KEY", true)}\"")
            buildConfigField("String", "FASHN_API_KEY",        "\"${upstreamSecret("FASHN_API_KEY", true)}\"")
            buildConfigField("String", "UNSPLASH_ACCESS_KEY",  "\"${upstreamSecret("UNSPLASH_ACCESS_KEY", true)}\"")
            buildConfigField("String", "GOOGLE_SEARCH_KEY",    "\"${upstreamSecret("GOOGLE_SEARCH_KEY", true)}\"")
            buildConfigField("String", "GOOGLE_SEARCH_CX",     "\"${upstreamSecret("GOOGLE_SEARCH_CX", true)}\"")
            buildConfigField("String", "EBAY_CLIENT_ID",       "\"${upstreamSecret("EBAY_CLIENT_ID", true)}\"")
            buildConfigField("String", "EBAY_CLIENT_SECRET",   "\"${upstreamSecret("EBAY_CLIENT_SECRET", true)}\"")
            buildConfigField("String", "RAPID_API_KEY",        "\"${upstreamSecret("RAPID_API_KEY", true)}\"")
            buildConfigField("String", "SERP_API_KEY",         "\"${upstreamSecret("SERP_API_KEY", true)}\"")
            buildConfigField("String", "SCRAPER_API_KEY",      "\"${upstreamSecret("SCRAPER_API_KEY", true)}\"")

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed with the debug keystore for now (personal/test distribution).
            // Replace with a real release signingConfig before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Parsers log when they swallow a malformed payload; the android.jar
            // stub throws on any Log call, which fails the very tests that feed
            // them malformed payloads on purpose.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Navigation
    implementation(libs.androidx.navigation.compose)
    // DataStore
    implementation(libs.androidx.datastore)
    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    // Coil
    implementation(libs.coil.compose)
    // Gson
    implementation(libs.gson)
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Material Icons Extended
    implementation(libs.androidx.material.icons.extended)
    // Accompanist Permissions
    implementation(libs.accompanist.permissions)
    // ML Kit Subject Segmentation (background removal)
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.kotlinx.coroutines.play.services)
    // Firebase — versions provided by BoM, do not add version strings here
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-functions")
    implementation(libs.play.services.auth)
    // Google Play Billing
    implementation(libs.billing.ktx)
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    // android.jar stubs org.json in unit tests: every call throws "not mocked",
    // which parsers with a catch-all would silently report as a parse failure.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
