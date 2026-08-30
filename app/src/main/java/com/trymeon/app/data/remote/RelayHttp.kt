package com.trymeon.app.data.remote

import android.util.Log
import com.trymeon.app.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Routes outbound API calls through our Cloud Functions relay so that upstream
 * credentials never ship inside the APK.
 *
 * Services keep building requests against the real upstream URL; the
 * interceptor rewrites the request just before it goes out, strips whatever
 * client-side key was attached, and swaps in the caller's Firebase ID token.
 * That keeps the ~24 call sites in [ClaudeApiService] and friends untouched.
 *
 * When [RELAY_BASE] is empty (no RELAY_BASE_URL in local.properties) the
 * interceptor is not installed at all and requests go direct using local keys,
 * which is how the debug loop stays fast.
 */
object RelayHttp {

    private const val TAG = "RelayHttp"

    /** e.g. https://us-central1-mycloset-ce07e.cloudfunctions.net/relay */
    private val RELAY_BASE: HttpUrl? =
        BuildConfig.RELAY_BASE_URL.trim().trimEnd('/')
            .takeIf { it.isNotEmpty() }?.toHttpUrlOrNull()

    val enabled: Boolean get() = RELAY_BASE != null

    /**
     * An [OkHttpClient.Builder] pre-wired for relaying. Services should use this
     * instead of `OkHttpClient.Builder()` so a new call site cannot silently go
     * direct with an embedded key.
     */
    fun builder(): OkHttpClient.Builder = OkHttpClient.Builder().apply {
        if (enabled) addInterceptor(RelayInterceptor)
    }

    private object RelayInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val base = RELAY_BASE
            val target = RelayRouting.targetFor(original.url)

            // Not an upstream we proxy (Firebase Storage, image CDNs, wttr.in …).
            if (base == null || target == null) return chain.proceed(original)

            val token = idToken()
                ?: throw IOException("Not signed in — cannot reach the API relay")

            val relayed = RelayRouting.relayUrl(base, original.url)

            val request = original.newBuilder()
                .url(relayed)
                .header("Authorization", "Bearer $token")
                .header("X-Relay-Target", target)
                .removeHeader("x-rapidapi-key")
                .removeHeader("X-RapidAPI-Key")
                .apply {
                    if (target == "rapidapi") header("X-Relay-Host", original.url.host)
                }
                .build()

            return chain.proceed(request)
        }
    }

    /**
     * Blocking fetch of the caller's Firebase ID token. Safe here: interceptors
     * already run off the main thread on OkHttp's dispatcher. Anonymous sign-in
     * covers the user who has not created an account yet — the relay only needs
     * a stable uid to meter against.
     */
    private fun idToken(): String? {
        // No cloud means no token, which the caller reports as "not signed in"
        // rather than dying inside an interceptor.
        val auth = com.trymeon.app.data.auth.CloudIdentity.auth() ?: return null
        val user = auth.currentUser ?: runCatching {
            Tasks.await(auth.signInAnonymously(), 20, TimeUnit.SECONDS).user
        }.onFailure { Log.e(TAG, "anonymous sign-in failed: ${it.message}") }.getOrNull()
        ?: return null

        // getIdToken(false) serves the SDK's cached token until it actually expires.
        return runCatching {
            Tasks.await(user.getIdToken(false), 20, TimeUnit.SECONDS).token
        }.onFailure { Log.e(TAG, "token fetch failed: ${it.message}") }.getOrNull()
    }
}
