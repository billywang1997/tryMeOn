package com.example.myapplication.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val TAG = "AmazonAPI"
private const val HOST = "webservices.amazon.com.au"
private const val REGION = "us-east-1"
private const val SERVICE = "ProductAdvertisingAPI"
private const val ALGORITHM = "AWS4-HMAC-SHA256"
private const val TARGET = "com.amazon.paapi5.v1.ProductAdvertisingAPIv1.SearchItems"
private const val PATH = "/paapi5/searchitems"

data class AmazonItem(
    val asin: String = "",
    val title: String = "",
    val price: String = "",
    val imageUrl: String = "",
    val itemWebUrl: String = ""
)

private data class PaApiResponse(
    @SerializedName("SearchResult") val searchResult: PaSearchResult? = null,
    @SerializedName("Errors") val errors: List<PaError>? = null
)
private data class PaSearchResult(@SerializedName("Items") val items: List<PaItem>? = null)
private data class PaItem(
    @SerializedName("ASIN") val asin: String = "",
    @SerializedName("DetailPageURL") val detailPageURL: String = "",
    @SerializedName("Images") val images: PaImages? = null,
    @SerializedName("ItemInfo") val itemInfo: PaItemInfo? = null,
    @SerializedName("Offers") val offers: PaOffers? = null
)
private data class PaImages(@SerializedName("Primary") val primary: PaImageSet? = null)
private data class PaImageSet(@SerializedName("Medium") val medium: PaImage? = null)
private data class PaImage(@SerializedName("URL") val url: String = "")
private data class PaItemInfo(@SerializedName("Title") val title: PaTitle? = null)
private data class PaTitle(@SerializedName("DisplayValue") val displayValue: String = "")
private data class PaOffers(@SerializedName("Listings") val listings: List<PaListing>? = null)
private data class PaListing(@SerializedName("Price") val price: PaPrice? = null)
private data class PaPrice(@SerializedName("DisplayAmount") val displayAmount: String = "")
private data class PaError(@SerializedName("Message") val message: String = "")

class AmazonApiService {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor { Log.d(TAG, it) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        accessKey: String,
        secretKey: String,
        associateTag: String,
        query: String,
        itemCount: Int = 10
    ): Result<List<AmazonItem>> = withContext(Dispatchers.IO) {
        if (accessKey.isBlank() || secretKey.isBlank() || associateTag.isBlank()) {
            return@withContext Result.failure(Exception("Amazon credentials not configured"))
        }
        try {
            val payload = gson.toJson(mapOf(
                "Keywords" to query,
                "Marketplace" to "www.amazon.com.au",
                "PartnerTag" to associateTag,
                "PartnerType" to "Associates",
                "Resources" to listOf(
                    "Images.Primary.Medium",
                    "ItemInfo.Title",
                    "Offers.Listings.Price"
                ),
                "SearchIndex" to "Fashion",
                "ItemCount" to itemCount
            ))

            val resp = client.newCall(buildSignedRequest(accessKey, secretKey, payload)).execute()
            val body = resp.body?.string() ?: ""
            Log.d(TAG, "status=${resp.code}")

            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Amazon ${resp.code}: $body"))
            }

            val result = gson.fromJson(body, PaApiResponse::class.java)
            val items = result.searchResult?.items?.mapNotNull { item ->
                val title = item.itemInfo?.title?.displayValue?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                AmazonItem(
                    asin = item.asin,
                    title = title,
                    price = item.offers?.listings?.firstOrNull()?.price?.displayAmount ?: "",
                    imageUrl = item.images?.primary?.medium?.url ?: "",
                    itemWebUrl = item.detailPageURL
                )
            } ?: emptyList()

            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun buildSignedRequest(accessKey: String, secretKey: String, payload: String): Request {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        val payloadHash = sha256Hex(payload)
        val canonicalHeaders =
            "content-encoding:amz-1.0\n" +
            "content-type:application/json; charset=utf-8\n" +
            "host:$HOST\n" +
            "x-amz-date:$amzDate\n" +
            "x-amz-target:$TARGET\n"
        val signedHeaders = "content-encoding;content-type;host;x-amz-date;x-amz-target"

        val canonicalRequest = "POST\n$PATH\n\n$canonicalHeaders\n$signedHeaders\n$payloadHash"
        val credentialScope = "$dateStamp/$REGION/$SERVICE/aws4_request"
        val stringToSign = "$ALGORITHM\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"

        val signingKey = getSigningKey(secretKey, dateStamp)
        val signature = hmacHex(signingKey, stringToSign)
        val auth = "$ALGORITHM Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        return Request.Builder()
            .url("https://$HOST$PATH")
            .header("Content-Encoding", "amz-1.0")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-Amz-Date", amzDate)
            .header("X-Amz-Target", TARGET)
            .header("Authorization", auth)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    private fun getSigningKey(secretKey: String, dateStamp: String): ByteArray {
        val kDate    = hmac("AWS4$secretKey".toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion  = hmac(kDate, REGION)
        val kService = hmac(kRegion, SERVICE)
        return hmac(kService, "aws4_request")
    }

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacHex(key: ByteArray, data: String) =
        hmac(key, data).joinToString("") { "%02x".format(it) }

    private fun sha256Hex(data: String) =
        MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
