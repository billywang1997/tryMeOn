package com.example.myapplication.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.media.FaceDetector
import android.util.Base64
import android.util.Log
import com.example.myapplication.data.remote.UnsplashService
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "FashnAPI"

data class FashnInputs(
    @SerializedName("model_image")   val modelImage: String,
    @SerializedName("garment_image") val garmentImage: String,
    val category: String
)

data class FashnRequest(
    @SerializedName("model_name") val modelName: String = "tryon-v1.6",
    val inputs: FashnInputs
)

data class FaceToModelInputs(
    @SerializedName("face_image") val faceImage: String
)

data class FaceToModelRequest(
    @SerializedName("model_name") val modelName: String = "face-to-model",
    val inputs: FaceToModelInputs
)

data class FashnRunResponse(val id: String? = null, val error: String? = null)
data class FashnStatusResponse(
    val id: String? = null,
    val status: String? = null,
    val output: List<String>? = null,
    val error: String? = null
)

class ReplicateApiService {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor { Log.d(TAG, it) }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val BASE = "https://api.fashn.ai"

    // Step 1: convert face photo → full-body model image URL
    suspend fun faceToModel(
        apiKey: String,
        faceImagePath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rawBm = loadBitmap(faceImagePath)
                ?: return@withContext Result.failure(Exception("Unable to read face photo"))

            val faceBm = cropFaceRegion(rawBm)
            Log.d(TAG, "face crop: original=${rawBm.width}x${rawBm.height} cropped=${faceBm.width}x${faceBm.height}")

            val faceB64 = bitmapToBase64(faceBm)
            val body = FaceToModelRequest(
                inputs = FaceToModelInputs(
                    faceImage = "data:image/jpeg;base64,$faceB64"
                )
            )

            val jsonStr = gson.toJson(body)
            Log.d(TAG, "face-to-model json prefix=${jsonStr.take(300)}")

            val runReq = Request.Builder()
                .url("$BASE/v1/run")
                .header("Authorization", "Bearer $apiKey")
                .post(jsonStr.toRequestBody(JSON))
                .build()

            val runResp = client.newCall(runReq).execute()
            val runBody = runResp.body?.string() ?: ""
            Log.d(TAG, "face-to-model run status=${runResp.code} body=$runBody")

            if (!runResp.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to generate model ${runResp.code}: $runBody"))
            }

            val run = gson.fromJson(runBody, FashnRunResponse::class.java)
            val id = run.id ?: return@withContext Result.failure(Exception(run.error ?: "Failed to get task ID"))

            pollUntilDone(apiKey, id, "face-to-model")
        } catch (e: Exception) {
            Result.failure(Exception("Model generation error: ${e.message}"))
        }
    }

    // Step 2: dress the model body with the selected garment
    suspend fun tryOn(
        apiKey: String,
        personImagePath: String,       // file path OR https:// URL (e.g. from faceToModel)
        garmentImagePath: String,
        garmentDescription: String,
        overrideCategory: String? = null  // if null, inferred from description
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Pass CDN URLs directly to avoid re-encoding quality loss on chained try-ons
            val personImage = if (personImagePath.startsWith("http")) {
                personImagePath
            } else {
                "data:image/jpeg;base64,${encodeFromFile(personImagePath)
                    ?: return@withContext Result.failure(Exception("Unable to read person photo, please re-upload"))}"
            }

            val resolvedGarmentPath = if (garmentImagePath.startsWith("unsplash:")) {
                UnsplashService.resolveUrl(garmentImagePath.removePrefix("unsplash:"))
                    ?: return@withContext Result.failure(Exception("Unable to get garment image URL"))
            } else garmentImagePath

            val garmentImage = if (resolvedGarmentPath.startsWith("http")) {
                resolvedGarmentPath
            } else {
                // High quality garment encoding — preserves colour accuracy for the AI model
                "data:image/jpeg;base64,${encodeGarmentFromFile(resolvedGarmentPath)
                    ?: return@withContext Result.failure(Exception("Unable to read garment image"))}"
            }

            val category = overrideCategory ?: inferCategory(garmentDescription)
            Log.d(TAG, "category=$category desc=$garmentDescription")

            val body = FashnRequest(
                inputs = FashnInputs(
                    modelImage   = personImage,
                    garmentImage = garmentImage,
                    category     = category
                )
            )

            val jsonStr = gson.toJson(body)
            Log.d(TAG, "tryon request prefix=${jsonStr.take(300)}")

            val runReq = Request.Builder()
                .url("$BASE/v1/run")
                .header("Authorization", "Bearer $apiKey")
                .post(jsonStr.toRequestBody(JSON))
                .build()

            val runResp = client.newCall(runReq).execute()
            val runBody = runResp.body?.string() ?: ""
            Log.d(TAG, "tryon run status=${runResp.code} body=$runBody")

            if (!runResp.isSuccessful) {
                return@withContext Result.failure(Exception("Submission failed ${runResp.code}: $runBody"))
            }

            val run = gson.fromJson(runBody, FashnRunResponse::class.java)
            val id = run.id ?: return@withContext Result.failure(Exception(run.error ?: "Failed to get task ID"))

            pollUntilDone(apiKey, id, "tryon")
        } catch (e: Exception) {
            Result.failure(Exception("Request error: ${e.message}"))
        }
    }

    private fun pollUntilDone(apiKey: String, id: String, tag: String): Result<String> {
        repeat(40) {
            Thread.sleep(3000)
            val stReq = Request.Builder()
                .url("$BASE/v1/status/$id")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val stResp = client.newCall(stReq).execute()
            val stBody = stResp.body?.string() ?: ""
            Log.d(TAG, "$tag status body=$stBody")

            val st = gson.fromJson(stBody, FashnStatusResponse::class.java)
            when (st.status) {
                "completed" -> {
                    val url = st.output?.firstOrNull()
                        ?: return Result.failure(Exception("No image returned"))
                    return Result.success(url)
                }
                "failed" -> return Result.failure(Exception(st.error ?: "Task failed"))
            }
        }
        return Result.failure(Exception("Timed out, please try again later"))
    }

    // Detect and crop the face region; returns original bitmap if no face found
    private fun cropFaceRegion(bm: Bitmap): Bitmap {
        return try {
            val rgb565 = bm.copy(Bitmap.Config.RGB_565, false)
            val detector = FaceDetector(rgb565.width, rgb565.height, 1)
            val faces = arrayOfNulls<FaceDetector.Face>(1)
            val found = detector.findFaces(rgb565, faces)
            if (found > 0) {
                val face = faces[0]!!
                val mid = PointF()
                face.getMidPoint(mid)
                val pad = face.eyesDistance() * 3.5f
                val left  = (mid.x - pad).toInt().coerceAtLeast(0)
                val top   = (mid.y - pad * 1.6f).toInt().coerceAtLeast(0)
                val right = (mid.x + pad).toInt().coerceAtMost(bm.width)
                val bot   = (mid.y + pad * 1.2f).toInt().coerceAtMost(bm.height)
                val w = right - left; val h = bot - top
                if (w > 32 && h > 32) Bitmap.createBitmap(bm, left, top, w, h) else bm
            } else {
                Log.d(TAG, "no face detected, using full image")
                bm
            }
        } catch (e: Exception) {
            Log.d(TAG, "face detection error: ${e.message}")
            bm
        }
    }

    private fun inferCategory(desc: String): String {
        val d = desc.lowercase()
        return when {
            d.contains("bottom") || d.contains("pants") || d.contains("skirt") || d.contains("jeans") || d.contains("shorts") -> "bottoms"
            d.contains("one-piece") || d.contains("dress") || d.contains("jumpsuit") || d.contains("romper") -> "one-pieces"
            else -> "tops"
        }
    }

    private fun loadBitmap(path: String): Bitmap? = if (path.startsWith("http")) {
        try {
            val req = Request.Builder().url(path).build()
            val bytes = client.newCall(req).execute().use { it.body?.bytes() } ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) { null }
    } else {
        try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null }
    }

    private fun encodeFromFile(path: String): String? =
        try { BitmapFactory.decodeFile(path)?.let { bitmapToBase64(it) } } catch (e: Exception) { null }

    private fun encodeGarmentFromFile(path: String): String? =
        try { BitmapFactory.decodeFile(path)?.let { bitmapToHighQualityBase64(it) } } catch (e: Exception) { null }

    private fun bitmapToHighQualityBase64(bm: Bitmap): String {
        val resized = resize(bm, 2048)
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 98, out)
        Log.d(TAG, "garment encoded ${out.size()} bytes ${resized.width}x${resized.height}")
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun downloadToBase64(url: String): String? = try {
        val req = Request.Builder().url(url).build()
        val bytes = client.newCall(req).execute().use { it.body?.bytes() } ?: return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmapToBase64(it) }
    } catch (e: Exception) { null }

    private fun bitmapToBase64(bm: Bitmap): String {
        val resized = resize(bm, 1024)
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 92, out)
        Log.d(TAG, "encoded ${out.size()} bytes ${resized.width}x${resized.height}")
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun resize(bm: Bitmap, max: Int): Bitmap {
        val w = bm.width; val h = bm.height
        val longest = maxOf(w, h)
        return when {
            longest < 512 -> {
                val r = 512f / longest
                Bitmap.createScaledBitmap(bm, (w * r).toInt().coerceAtLeast(1), (h * r).toInt().coerceAtLeast(1), true)
            }
            longest > max -> {
                val r = max.toFloat() / longest
                Bitmap.createScaledBitmap(bm, (w * r).toInt(), (h * r).toInt(), true)
            }
            else -> bm
        }
    }
}
