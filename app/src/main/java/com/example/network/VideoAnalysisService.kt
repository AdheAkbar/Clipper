package com.example.network

import android.graphics.Bitmap
import android.util.Base64
import com.example.data.AppSettings
import com.example.data.SettingsManager
import com.example.model.ContentPreset
import com.example.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class VideoAnalysisService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(settings: AppSettings): String = withContext(Dispatchers.IO) {
        val endpoint = SettingsManager.normalizeChatEndpoint(settings.baseUrl)

        val pingJson = JSONObject().apply {
            put("model", settings.modelName)
            put("stream", false)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Respond with 'CONNECTED' if you receive this.")
                })
            })
            put("max_tokens", 15)
        }

        val authToken = if (settings.apiKey.isNotBlank()) settings.apiKey.trim() else "9router"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(pingJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(body).optJSONObject("error")?.optString("message") ?: body
                } catch (e: Exception) {
                    body.take(200)
                }
                throw IOException("HTTP ${response.code}: $errorMsg")
            }
            val reply = extractContentFromResponseBody(body).ifBlank { "Connected" }
            "SUCCESS (${endpoint}): ${reply.trim()}"
        } catch (e: Exception) {
            if (e is ConnectException || e is SocketTimeoutException) {
                val myIps = LanScanner.getDeviceLanIps()
                val ipInfo = if (myIps.isNotEmpty()) "IP Wi-Fi HP Anda saat ini: ${myIps.joinToString(", ")}" else "HP belum terhubung ke Wi-Fi LAN"
                val hint = if (endpoint.contains("10.0.2.2") && !LanScanner.isEmulator()) {
                    "⚠️ Anda menggunakan HP Fisik tetapi URL masih '10.0.2.2' (khusus Emulator)!\n" +
                        "Gunakan tombol '🔍 Cari Otomatis IP 9Router' atau ganti 10.0.2.2 dengan IP LAN PC Anda."
                } else {
                    "Pastikan:\n1. PC & HP di Wi-Fi yang sama ($ipInfo)\n2. 9Router di PC jalan di 0.0.0.0:20128\n3. Firewall Windows tidak memblokir port 20128 (atau gunakan URL 9Remote)."
                }
                throw IOException("Gagal menghubungi '$endpoint'.\n$hint")
            }
            throw e
        }
    }

    suspend fun analyzeFrames(
        frames: List<Pair<Long, Bitmap>>,
        maxClips: Int,
        preset: ContentPreset,
        settings: AppSettings,
        onStatusUpdate: (String) -> Unit = {},
        onEndpointAutoUpdated: (String) -> Unit = {}
    ): List<VideoClip> = withContext(Dispatchers.IO) {
        if (settings.provider == SettingsManager.PROVIDER_GEMINI_DIRECT && settings.apiKey.isBlank()) {
            throw IllegalArgumentException("API Key kosong untuk Gemini Direct. Silakan isi di Settings.")
        }

        if (settings.provider == SettingsManager.PROVIDER_GEMINI_DIRECT) {
            analyzeWithGeminiDirect(frames, maxClips, preset, settings)
        } else {
            analyzeWith9Router(frames, maxClips, preset, settings, onStatusUpdate, onEndpointAutoUpdated)
        }
    }

    private suspend fun analyzeWith9Router(
        frames: List<Pair<Long, Bitmap>>,
        maxClips: Int,
        preset: ContentPreset,
        settings: AppSettings,
        onStatusUpdate: (String) -> Unit,
        onEndpointAutoUpdated: (String) -> Unit
    ): List<VideoClip> {
        var activeBaseUrl = settings.baseUrl

        val isLocalEmulatorAddress = activeBaseUrl.contains("10.0.2.2") ||
                activeBaseUrl.contains("localhost") ||
                activeBaseUrl.contains("127.0.0.1")

        if (isLocalEmulatorAddress && !LanScanner.isEmulator()) {
            onStatusUpdate("Mencari otomatis server 9Router (port 20128) di Wi-Fi Anda...")
            val discoveredUrl = LanScanner.discover9RouterOnLan { progress ->
                onStatusUpdate(progress)
            }
            if (discoveredUrl != null) {
                activeBaseUrl = discoveredUrl
                onEndpointAutoUpdated(discoveredUrl)
                onStatusUpdate("Ditemukan 9Router di $discoveredUrl! Mengirim frame...")
            } else {
                val myIps = LanScanner.getDeviceLanIps()
                val subnetHint = if (myIps.isNotEmpty()) {
                    val sample = myIps.first()
                    val prefix = sample.substringBeforeLast(".")
                    "IP Wi-Fi HP Anda adalah $sample. Masukkan IP PC Anda (contoh: http://$prefix.X:20128/v1)"
                } else {
                    "Pastikan HP terhubung ke Wi-Fi yang sama dengan PC"
                }
                throw IOException(
                    "Alamat '10.0.2.2' hanya untuk Emulator, tidak bisa dipakai di HP Fisik!\n\n" +
                    "Pemindaian otomatis tidak menemukan port 20128 yang terbuka di Wi-Fi Anda.\n" +
                    "Solusi:\n" +
                    "1. $subnetHint pada kotak 'Server 9Router' di atas.\n" +
                    "2. Pastikan di PC, 9Router berjalan di 0.0.0.0:20128 dan Firewall Windows mengizinkan port 20128.\n" +
                    "3. Atau salin URL publik dari menu '9Remote' di 9Router PC Anda."
                )
            }
        }

        val endpoint = SettingsManager.normalizeChatEndpoint(activeBaseUrl)

        val systemPrompt = """
            You are an elite video editor and viral content strategist.
            You are evaluating chronological frame snapshots from a video with timestamps.
            Identify the top $maxClips most engaging highlight segments based on the preset.
            
            Preset: ${preset.title}
            Focus Guidelines:
            ${preset.promptFocus}
            
            Format Requirements:
            Return ONLY a valid JSON array of objects.
            Keep all text clean, editorial, natural, and professional (do NOT use excessive emojis or clickbait).
            Each object must contain:
            - "start": Timestamp string formatted as "MM:SS" (where highlight starts)
            - "end": Timestamp string formatted as "MM:SS" (where highlight ends, clip length 15-60s)
            - "score": Double between 0.50 and 1.00 indicating retention/viral rating
            - "title": Clean, compelling editorial headline for the clip (max 7 words, no emojis)
            - "hook": Concise, natural opening statement or context subtitle (no emojis)
            - "captions": Array of 2-3 clean, natural subtitle lines describing the key spoken/visual progression of this specific segment (no emojis)
            - "fypHashtags": 4-5 relevant hashtags (e.g. "#shorts #reels #podcast #highlights")
            - "reason": Brief editorial explanation of why this moment has strong viewer retention
            - "category": Clean category label (e.g. "Highlight", "Insight", "Story", "Key Moment")
        """.trimIndent()

        val userParts = JSONArray()
        userParts.put(
            JSONObject().apply {
                put("type", "text")
                put("text", "Evaluate these chronological video frames and identify up to $maxClips top viral clips:")
            }
        )

        frames.forEach { (timeMs, bmp) ->
            val timeSec = timeMs / 1000
            val min = timeSec / 60
            val sec = timeSec % 60
            val tsStr = String.format("%02d:%02d", min, sec)

            val base64Str = bitmapToBase64(bmp)

            userParts.put(
                JSONObject().apply {
                    put("type", "text")
                    put("text", "Frame at [$tsStr]:")
                }
            )

            val imageContent = JSONObject().apply {
                put("type", "image_url")
                put(
                    "image_url",
                    JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$base64Str")
                    }
                )
            }
            userParts.put(imageContent)
        }

        val messages = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                }
            )
            put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", userParts)
                }
            )
        }

        // Explicitly disable streaming ("stream": false) so 9Router returns 1 complete JSON response
        val requestJson = JSONObject().apply {
            put("model", settings.modelName)
            put("messages", messages)
            put("stream", false)
            put("temperature", 0.4)
        }

        val authToken = if (settings.apiKey.isNotBlank()) settings.apiKey.trim() else "9router"

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("HTTP-Referer", "https://aistudio.google.com")
            .addHeader("X-Title", "Auto Video Clipper")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            if (e is ConnectException || e is SocketTimeoutException) {
                val myIps = LanScanner.getDeviceLanIps()
                val ipInfo = if (myIps.isNotEmpty()) "IP Wi-Fi HP: ${myIps.first()}" else "HP tidak di Wi-Fi"
                throw IOException(
                    "Gagal terhubung ke 9Router di '$endpoint' ($ipInfo).\n" +
                    "Pastikan:\n" +
                    "1. IP LAN PC sudah benar (bukan 10.0.2.2 jika di HP fisik).\n" +
                    "2. Server 9Router di PC aktif pada 0.0.0.0:20128.\n" +
                    "3. Firewall PC mengizinkan port 20128."
                )
            }
            throw e
        }

        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errObj = JSONObject(responseBody)
                errObj.optJSONObject("error")?.optString("message") ?: responseBody
            } catch (e: Exception) {
                responseBody.take(300)
            }
            throw IOException("9Router Error (${response.code}): $errorMsg")
        }

        val rawText = extractContentFromResponseBody(responseBody)
        if (rawText.isBlank()) {
            throw IOException("Respon dari 9Router kosong. Coba ulangi analisis.")
        }

        val cleanJson = extractJson(rawText)
        return parseClipsFromJson(cleanJson)
    }

    private fun analyzeWithGeminiDirect(
        frames: List<Pair<Long, Bitmap>>,
        maxClips: Int,
        preset: ContentPreset,
        settings: AppSettings
    ): List<VideoClip> {
        val model = if (settings.modelName.contains("/")) {
            settings.modelName.substringAfterLast("/")
        } else {
            settings.modelName
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${settings.apiKey}"

        val partsArray = JSONArray()
        val promptText = """
            You are an elite video editor and viral content strategist.
            Identify the top $maxClips most engaging highlight segments based on preset: ${preset.title}.
            Focus Guidelines:
            ${preset.promptFocus}
            
            Output strictly valid JSON array without markdown formatting. Format:
            [
              {
                "start": "MM:SS",
                "end": "MM:SS",
                "score": 0.95,
                "title": "Shocking Twist",
                "hook": "Wait until you see this...",
                "reason": "Engaging climax and funny reaction",
                "category": "😂 FUNNY"
              }
            ]
        """.trimIndent()

        partsArray.put(JSONObject().apply { put("text", promptText) })

        frames.forEach { (timeMs, bmp) ->
            val timeSec = timeMs / 1000
            val min = timeSec / 60
            val sec = timeSec % 60
            val tsStr = String.format("[%02d:%02d]", min, sec)

            val base64Str = bitmapToBase64(bmp)

            partsArray.put(JSONObject().apply { put("text", "Timestamp $tsStr") })
            partsArray.put(
                JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Str)
                    })
                }
            )
        }

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply { put("parts", partsArray) })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw IOException("Gemini API Error (${response.code}): $responseBody")
        }

        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates") ?: return emptyList()
        if (candidates.length() == 0) return emptyList()
        val content = candidates.getJSONObject(0).optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val text = parts?.getJSONObject(0)?.optString("text") ?: "[]"

        return parseClipsFromJson(extractJson(text))
    }

    companion object {
        /**
         * Extracts the assistant's text content from either:
         * 1. A standard non-streaming JSON response (`choices[0].message.content`)
         * 2. A Server-Sent Events (SSE) stream (`data: {"choices":[{"delta":{"content":"..."}}]}`)
         *    in case 9Router or an upstream provider still streams chunks.
         */
        fun extractContentFromResponseBody(responseBody: String): String {
            val trimmed = responseBody.trim()
            if (trimmed.isEmpty()) return ""

            // Case 1: Server-Sent Events (SSE) stream starting with "data:" or containing "\ndata:"
            if (trimmed.startsWith("data:") || trimmed.contains("\ndata:")) {
                val sb = StringBuilder()
                val lines = trimmed.lines()
                for (line in lines) {
                    val cleanLine = line.trim()
                    if (!cleanLine.startsWith("data:")) continue
                    val payload = cleanLine.removePrefix("data:").trim()
                    if (payload == "[DONE]" || payload.isEmpty()) continue
                    try {
                        val chunkObj = JSONObject(payload)
                        val choices = chunkObj.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice0 = choices.getJSONObject(0)
                            val delta = choice0.optJSONObject("delta")
                            if (delta != null && delta.has("content") && !delta.isNull("content")) {
                                sb.append(delta.optString("content", ""))
                            } else {
                                val message = choice0.optJSONObject("message")
                                if (message != null && message.has("content") && !message.isNull("content")) {
                                    sb.append(message.optString("content", ""))
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore non-JSON SSE comments or keepalive lines
                    }
                }
                if (sb.isNotEmpty()) {
                    return sb.toString()
                }
            }

            // Case 2: Standard single JSON object response ("stream": false)
            return try {
                val root = JSONObject(trimmed)
                val choices = root.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val choice0 = choices.getJSONObject(0)
                    val message = choice0.optJSONObject("message")
                    when {
                        message != null && !message.isNull("content") -> {
                            val contentVal = message.get("content")
                            if (contentVal is JSONArray) {
                                // Handle array of content parts if returned by some providers
                                val partSb = StringBuilder()
                                for (i in 0 until contentVal.length()) {
                                    val part = contentVal.optJSONObject(i)
                                    if (part != null) {
                                        partSb.append(part.optString("text", ""))
                                    }
                                }
                                partSb.toString()
                            } else {
                                message.optString("content", "")
                            }
                        }
                        choice0.has("text") -> choice0.optString("text", "")
                        else -> ""
                    }
                } else if (root.has("content")) {
                    root.optString("content", "")
                } else {
                    trimmed
                }
            } catch (e: Exception) {
                trimmed
            }
        }

        /**
         * Extracts the clean JSON array string from markdown code blocks (e.g. ```json ... ```).
         */
        fun extractJson(raw: String): String {
            val clean = if (raw.contains("```json", ignoreCase = true)) {
                val afterTag = raw.substringAfter("```json", missingDelimiterValue = raw.substringAfter("```JSON"))
                afterTag.substringBeforeLast("```").trim()
            } else if (raw.contains("```")) {
                raw.substringAfter("```").substringBeforeLast("```").trim()
            } else {
                raw.trim()
            }

            val startIdx = clean.indexOf('[')
            val endIdx = clean.lastIndexOf(']')
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                return clean.substring(startIdx, endIdx + 1).trim()
            }
            return if (clean.startsWith("[")) clean else raw.trim()
        }

        /**
         * Parses the extracted JSON string into a list of VideoClip objects for the UI cards.
         * Supports both "start"/"end" and "startStr"/"endStr" field conventions.
         */
        fun parseClipsFromJson(rawText: String): List<VideoClip> {
            val results = mutableListOf<VideoClip>()
            try {
                val jsonText = extractJson(rawText)
                val array = if (jsonText.startsWith("{")) {
                    val obj = JSONObject(jsonText)
                    obj.optJSONArray("clips")
                        ?: obj.optJSONArray("highlights")
                        ?: obj.optJSONArray("data")
                        ?: JSONArray()
                } else {
                    JSONArray(jsonText)
                }

                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue

                    val startVal = item.optString("startStr").ifBlank {
                        item.optString("start").ifBlank {
                            item.optString("start_time").ifBlank {
                                item.optString("startTime", "00:00")
                            }
                        }
                    }

                    val endVal = item.optString("endStr").ifBlank {
                        item.optString("end").ifBlank {
                            item.optString("end_time").ifBlank {
                                item.optString("endTime", "00:15")
                            }
                        }
                    }

                    val rawScore = item.optDouble("score", item.optDouble("rating", 0.85))
                    val normalizedScore = if (rawScore > 1.0) (rawScore / 100.0).coerceIn(0.0, 1.0) else rawScore.coerceIn(0.0, 1.0)

                    val titleVal = item.optString("title").ifBlank {
                        item.optString("headline").ifBlank {
                            item.optString("judul", "Highlight #${i + 1}")
                        }
                    }

                    val hookVal = item.optString("hook").ifBlank {
                        item.optString("viral_hook").ifBlank {
                            item.optString("caption", titleVal)
                        }
                    }

                    val reasonVal = item.optString("reason").ifBlank {
                        item.optString("description").ifBlank {
                            item.optString("explanation").ifBlank {
                                item.optString("alasan", "Segmen dengan alur cerita dan retensi visual yang kuat.")
                            }
                        }
                    }

                    val categoryVal = item.optString("category").ifBlank {
                        item.optString("kategori").ifBlank {
                            item.optString("tag", "Highlight")
                        }
                    }

                    val parsedCaptions = mutableListOf<String>()
                    val capArray = item.optJSONArray("captions") ?: item.optJSONArray("subtitles")
                    if (capArray != null) {
                        for (c in 0 until capArray.length()) {
                            val line = capArray.optString(c).trim()
                            if (line.isNotEmpty()) parsedCaptions.add(line)
                        }
                    }
                    if (parsedCaptions.isEmpty()) {
                        if (hookVal.isNotBlank()) parsedCaptions.add(hookVal)
                        if (titleVal.isNotBlank() && titleVal != hookVal) parsedCaptions.add(titleVal)
                    }

                    val hashtagsVal = item.optString("fypHashtags").ifBlank {
                        item.optString("hashtags").ifBlank {
                            "#shorts #reels #highlights"
                        }
                    }

                    results.add(
                        VideoClip(
                            startStr = startVal,
                            endStr = endVal,
                            score = normalizedScore,
                            title = titleVal,
                            hook = hookVal,
                            reason = reasonVal,
                            category = categoryVal,
                            captions = parsedCaptions,
                            fypHashtags = hashtagsVal
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return results
        }
    }

    private fun bitmapToBase64(bmp: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
