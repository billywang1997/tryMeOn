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

        buildConfigField("String", "CLAUDE_API_KEY",            "\"${secret("CLAUDE_API_KEY")}\"")
        buildConfigField("String", "FASHN_API_KEY",             "\"${secret("FASHN_API_KEY")}\"")
        buildConfigField("String", "UNSPLASH_ACCESS_KEY",       "\"${secret("UNSPLASH_ACCESS_KEY")}\"")
        buildConfigField("String", "GOOGLE_SEARCH_KEY",         "\"${secret("GOOGLE_SEARCH_KEY")}\"")
        buildConfigField("String", "GOOGLE_SEARCH_CX",          "\"${secret("GOOGLE_SEARCH_CX")}\"")
        buildConfigField("String", "EBAY_CLIENT_ID",            "\"${secret("EBAY_CLIENT_ID")}\"")
        buildConfigField("String", "EBAY_CLIENT_SECRET",        "\"${secret("EBAY_CLIENT_SECRET")}\"")
        buildConfigField("String", "EBAY_AFFILIATE_CAMPAIGN_ID","\"${secret("EBAY_AFFILIATE_CAMPAIGN_ID")}\"")
        buildConfigField("String", "RAPID_API_KEY",             "\"${secret("RAPID_API_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
