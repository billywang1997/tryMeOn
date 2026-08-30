package com.trymeon.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class WeatherInfo(
    val tempC: Int,
    val feelsLikeC: Int,
    val description: String,
    val humidity: Int,
    val city: String
) {
    val descriptionZh: String get() = when {
        description.contains("Sunny", true) || description.contains("Clear", true) -> "Sunny"
        description.contains("Partly cloudy", true) -> "Partly Cloudy"
        description.contains("Cloudy", true) || description.contains("Overcast", true) -> "Cloudy"
        description.contains("Rain", true) || description.contains("Drizzle", true) -> "Rainy"
        description.contains("Snow", true) -> "Snowy"
        description.contains("Thunder", true) -> "Thunderstorm"
        description.contains("Fog", true) || description.contains("Mist", true) -> "Foggy"
        description.contains("Wind", true) -> "Windy"
        else -> description
    }

    val emoji: String get() = when {
        description.contains("Sunny", true) || description.contains("Clear", true) -> "☀️"
        description.contains("Partly cloudy", true) -> "⛅"
        description.contains("Cloudy", true) || description.contains("Overcast", true) -> "☁️"
        description.contains("Rain", true) || description.contains("Drizzle", true) -> "🌧️"
        description.contains("Snow", true) -> "❄️"
        description.contains("Thunder", true) -> "⛈️"
        description.contains("Fog", true) || description.contains("Mist", true) -> "🌫️"
        else -> "🌡️"
    }

    val dressHint: String get() = when {
        tempC >= 28 -> "Hot weather — go for light, breathable layers"
        tempC >= 22 -> "Comfortable weather — a tee or light jacket works great"
        tempC >= 15 -> "A little cool — bring a jacket"
        tempC >= 8  -> "Cold weather — layer up to stay warm"
        else        -> "Very cold — a heavy coat is a must"
    }
}

object WeatherService {
    // Bounded, because the card that waits on this has no other way to stop
    // saying "Fetching weather…". OkHttp's defaults are ten seconds each for
    // connect and read, which on a dead network is a card that spins forever.
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Detect the user's city from their IP address using ipinfo.io (no API key
     * required).
     *
     * On a tighter budget than the forecast itself: this call only refines a
     * city we already have a fallback for, and it runs before the forecast, so
     * whatever it spends is added to how long the card spins.
     */
    suspend fun detectCity(): String = withContext(Dispatchers.IO) {
        try {
            val body = client.newBuilder()
                .callTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(Request.Builder().url("https://ipinfo.io/json").build())
                .execute().use { it.body?.string() } ?: return@withContext ""
            org.json.JSONObject(body).optString("city", "")
        } catch (e: Exception) { "" }
    }

    suspend fun fetch(city: String): WeatherInfo? = withContext(Dispatchers.IO) {
        try {
            val encoded = city.trim().replace(" ", "+")
            val url = "https://wttr.in/$encoded?format=j1"
            val body = client.newCall(Request.Builder().url(url).build())
                .execute().use { it.body?.string() } ?: return@withContext null
            val cc = JSONObject(body).getJSONArray("current_condition").getJSONObject(0)
            WeatherInfo(
                tempC      = cc.getString("temp_C").toInt(),
                feelsLikeC = cc.getString("FeelsLikeC").toInt(),
                description = cc.getJSONArray("weatherDesc").getJSONObject(0).getString("value"),
                humidity   = cc.getString("humidity").toInt(),
                city       = city
            )
        } catch (e: Exception) { null }
    }
}
