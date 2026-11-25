package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.model.ApiProvider
import com.example.model.ChatMessage
import com.example.model.CustomApiSettings
import com.example.model.ParsedAiResponse
import com.example.model.PendingAttachment
import com.example.model.ResponseParser
import com.example.model.StylePreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "ExternalApiAiClient"
private const val EXTERNAL_TIMEOUT_MS = 60_000L

class ExternalApiAiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun streamReply(
        settings: CustomApiSettings,
        modelId: String,
        systemInstruction: String,
        historyMessages: List<ChatMessage>,
        userPrompt: String,
        attachment: PendingAttachment?,
        stylePreset: StylePreset
    ): Flow<ParsedAiResponse> = flow {
        val apiKey = settings.apiKey.trim()
        require(apiKey.isNotEmpty()) {
            "API key is missing. Tap the Key icon or Model Selector to add your API key."
        }
        val cleanBase = settings.baseUrl.trim().trimEnd('/')
        require(cleanBase.startsWith("http://") || cleanBase.startsWith("https://")) {
            "API Base URL must start with https:// or http://"
        }

        val request = when (settings.provider) {
            ApiProvider.OPENAI_COMPATIBLE -> buildOpenAiRequest(
                baseUrl = cleanBase,
                apiKey = apiKey,
                modelId = modelId,
                systemInstruction = systemInstruction,
                history = historyMessages,
                userPrompt = userPrompt,
                attachment = attachment,
                temperature = if (stylePreset == StylePreset.CODE_ARCHITECT) 0.25 else 0.7
            )
            ApiProvider.ANTHROPIC -> buildAnthropicRequest(
                baseUrl = cleanBase,
                apiKey = apiKey,
                modelId = modelId,
                systemInstruction = systemInstruction,
                history = historyMessages,
                userPrompt = userPrompt,
                attachment = attachment,
                temperature = if (stylePreset == StylePreset.CODE_ARCHITECT) 0.25 else 0.7
            )
            ApiProvider.GEMINI_REST -> buildGeminiRestRequest(
                baseUrl = cleanBase,
                apiKey = apiKey,
                modelId = modelId,
                systemInstruction = systemInstruction,
                history = historyMessages,
                userPrompt = userPrompt,
                attachment = attachment,
                temperature = if (stylePreset == StylePreset.CODE_ARCHITECT) 0.25 else 0.7
            )
        }

        val accumulated = StringBuilder()
        val thinkingAccumulated = StringBuilder()

        withTimeout(EXTERNAL_TIMEOUT_MS) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()?.take(500).orEmpty()
                    val parsedErr = extractApiError(errorBody)
                    throw IllegalStateException("API Error (${response.code}): $parsedErr")
                }

                val body = response.body ?: throw IllegalStateException("Empty response body from API.")
                val source = body.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break

                    val delta = parseSseDelta(settings.provider, payload)
                    if (delta.thinkingDelta.isNotEmpty()) {
                        thinkingAccumulated.append(delta.thinkingDelta)
                    }
                    if (delta.textDelta.isNotEmpty()) {
                        accumulated.append(delta.textDelta)
                    }
                    if (delta.thinkingDelta.isNotEmpty() || delta.textDelta.isNotEmpty()) {
                        emit(
                            mergeParsedWithNativeThinking(
                                rawVisible = accumulated.toString(),
                                nativeThinking = thinkingAccumulated.toString(),
                                isFinal = false
                            )
                        )
                    }
                }
            }
        }

        if (accumulated.isBlank() && thinkingAccumulated.isBlank()) {
            throw IllegalStateException("The external API returned an empty response.")
        }
        emit(
            mergeParsedWithNativeThinking(
                rawVisible = accumulated.toString(),
                nativeThinking = thinkingAccumulated.toString(),
                isFinal = true
            )
        )
    }.flowOn(Dispatchers.IO)

    suspend fun testConnection(settings: CustomApiSettings, modelId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = settings.apiKey.trim()
                if (apiKey.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Please enter an API key first."))
                }
                val cleanBase = settings.baseUrl.trim().trimEnd('/')
                if (!cleanBase.startsWith("http://") && !cleanBase.startsWith("https://")) {
                    return@withContext Result.failure(IllegalArgumentException("Base URL must start with https:// or http://"))
                }

                val startMs = System.currentTimeMillis()
                val req = when (settings.provider) {
                    ApiProvider.OPENAI_COMPATIBLE -> buildOpenAiRequest(
                        baseUrl = cleanBase,
                        apiKey = apiKey,
                        modelId = modelId,
                        systemInstruction = "Reply with OK.",
                        history = emptyList(),
                        userPrompt = "Ping",
                        attachment = null,
                        temperature = 0.0,
                        stream = false,
                        maxTokens = 16
                    )
                    ApiProvider.ANTHROPIC -> buildAnthropicRequest(
                        baseUrl = cleanBase,
                        apiKey = apiKey,
                        modelId = modelId,
                        systemInstruction = "Reply with OK.",
                        history = emptyList(),
                        userPrompt = "Ping",
                        attachment = null,
                        temperature = 0.0,
                        stream = false,
                        maxTokens = 16
                    )
                    ApiProvider.GEMINI_REST -> buildGeminiRestRequest(
                        baseUrl = cleanBase,
                        apiKey = apiKey,
                        modelId = modelId,
                        systemInstruction = "Reply with OK.",
                        history = emptyList(),
                        userPrompt = "Ping",
                        attachment = null,
                        temperature = 0.0,
                        stream = false
                    )
                }

                withTimeout(20_000L) {
                    client.newCall(req).execute().use { resp ->
                        val elapsed = System.currentTimeMillis() - startMs
                        if (resp.isSuccessful) {
                            Result.success("Connected to $modelId (${elapsed}ms)")
                        } else {
                            val err = extractApiError(resp.body?.string()?.take(400).orEmpty())
                            Result.failure(IllegalStateException("HTTP ${resp.code}: $err"))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Connection test failed: ${e.message}")
                Result.failure(e)
            }
        }

    private fun mergeParsedWithNativeThinking(
        rawVisible: String,
        nativeThinking: String,
        isFinal: Boolean
    ): ParsedAiResponse {
        val parsed = ResponseParser.parse(rawVisible, isFinal = isFinal)
        val combinedThinking = buildString {
            if (nativeThinking.isNotBlank()) append(nativeThinking.trim())
            if (!parsed.thinkingSummary.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(parsed.thinkingSummary)
            }
        }.takeIf { it.isNotBlank() }
        return parsed.copy(thinkingSummary = combinedThinking)
    }

    private fun buildOpenAiRequest(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        systemInstruction: String,
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: PendingAttachment?,
        temperature: Double,
        stream: Boolean = true,
        maxTokens: Int = 4096
    ): Request {
        val messages = JSONArray()
        if (systemInstruction.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
        }
        history.takeLast(16).forEach { msg ->
            val role = if (msg.role == "user") "user" else "assistant"
            val text = buildHistoryMessageText(msg)
            if (text.isNotBlank()) {
                messages.put(JSONObject().put("role", role).put("content", text))
            }
        }

        val currentContent: Any = when (attachment) {
            is PendingAttachment.ImageAttachment -> {
                val b64 = bitmapToBase64Jpeg(attachment.bitmap)
                JSONArray().apply {
                    put(JSONObject().put("type", "text").put("text", userPrompt.ifBlank { "Analyze this image." }))
                    put(
                        JSONObject().put("type", "image_url").put(
                            "image_url",
                            JSONObject().put("url", "data:image/jpeg;base64,$b64")
                        )
                    )
                }
            }
            is PendingAttachment.DocumentAttachment -> {
                val docText = attachment.extractedText
                    ?: "[Attached binary file: ${attachment.displayName} (${attachment.mimeType})]"
                "$userPrompt\n\nAttached file (${attachment.displayName}):\n$docText".trim()
            }
            null -> userPrompt
        }
        messages.put(JSONObject().put("role", "user").put("content", currentContent))

        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", messages)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", stream)
        }

        val url = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://zenvix.ai")
            .header("X-Title", "Zenvix AI")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildAnthropicRequest(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        systemInstruction: String,
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: PendingAttachment?,
        temperature: Double,
        stream: Boolean = true,
        maxTokens: Int = 4096
    ): Request {
        val messages = JSONArray()
        history.takeLast(16).forEach { msg ->
            val role = if (msg.role == "user") "user" else "assistant"
            val text = buildHistoryMessageText(msg)
            if (text.isNotBlank()) {
                messages.put(JSONObject().put("role", role).put("content", text))
            }
        }

        val currentContent: Any = when (attachment) {
            is PendingAttachment.ImageAttachment -> {
                val b64 = bitmapToBase64Jpeg(attachment.bitmap)
                JSONArray().apply {
                    put(
                        JSONObject().put("type", "image").put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", b64)
                        )
                    )
                    put(JSONObject().put("type", "text").put("text", userPrompt.ifBlank { "Analyze this image." }))
                }
            }
            is PendingAttachment.DocumentAttachment -> {
                val docText = attachment.extractedText
                    ?: "[Attached file: ${attachment.displayName} (${attachment.mimeType})]"
                "$userPrompt\n\nAttached file (${attachment.displayName}):\n$docText".trim()
            }
            null -> userPrompt
        }
        messages.put(JSONObject().put("role", "user").put("content", currentContent))

        val payload = JSONObject().apply {
            put("model", modelId)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("stream", stream)
            if (systemInstruction.isNotBlank()) {
                put("system", systemInstruction)
            }
            put("messages", messages)
        }

        val url = if (baseUrl.endsWith("/messages")) baseUrl else "$baseUrl/messages"
        return Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildGeminiRestRequest(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        systemInstruction: String,
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: PendingAttachment?,
        temperature: Double,
        stream: Boolean = true
    ): Request {
        val contents = JSONArray()
        history.takeLast(16).forEach { msg ->
            val role = if (msg.role == "user") "user" else "model"
            val text = buildHistoryMessageText(msg)
            if (text.isNotBlank()) {
                contents.put(
                    JSONObject()
                        .put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                )
            }
        }

        val parts = JSONArray()
        when (attachment) {
            is PendingAttachment.ImageAttachment -> {
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", bitmapToBase64Jpeg(attachment.bitmap))
                    )
                )
            }
            is PendingAttachment.DocumentAttachment -> {
                if (!attachment.extractedText.isNullOrBlank()) {
                    parts.put(JSONObject().put("text", "Attached file (${attachment.displayName}):\n${attachment.extractedText}"))
                } else {
                    parts.put(
                        JSONObject().put(
                            "inlineData",
                            JSONObject()
                                .put("mimeType", attachment.mimeType)
                                .put("data", Base64.encodeToString(attachment.bytes, Base64.NO_WRAP))
                        )
                    )
                }
            }
            null -> Unit
        }
        parts.put(JSONObject().put("text", userPrompt.ifBlank { "Analyze this attachment." }))
        contents.put(JSONObject().put("role", "user").put("parts", parts))

        val payload = JSONObject().apply {
            put("contents", contents)
            if (systemInstruction.isNotBlank()) {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                )
            }
            put("generationConfig", JSONObject().put("temperature", temperature))
        }

        val action = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
        val url = "$baseUrl/models/$modelId:$action"
        return Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private data class SseDelta(val textDelta: String = "", val thinkingDelta: String = "")

    private fun parseSseDelta(provider: ApiProvider, jsonStr: String): SseDelta {
        return try {
            val obj = JSONObject(jsonStr)
            when (provider) {
                ApiProvider.OPENAI_COMPATIBLE -> {
                    val choice = obj.optJSONArray("choices")?.optJSONObject(0) ?: return SseDelta()
                    val delta = choice.optJSONObject("delta") ?: return SseDelta()
                    val text = delta.optString("content", "")
                    val reasoning = delta.optString("reasoning_content", "")
                        .ifEmpty { delta.optString("reasoning", "") }
                    SseDelta(textDelta = text, thinkingDelta = reasoning)
                }
                ApiProvider.ANTHROPIC -> {
                    if (obj.optString("type") == "content_block_delta") {
                        val delta = obj.optJSONObject("delta") ?: return SseDelta()
                        when (delta.optString("type")) {
                            "text_delta" -> SseDelta(textDelta = delta.optString("text", ""))
                            "thinking_delta" -> SseDelta(thinkingDelta = delta.optString("thinking", ""))
                            else -> SseDelta()
                        }
                    } else {
                        SseDelta()
                    }
                }
                ApiProvider.GEMINI_REST -> {
                    val parts = obj.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts") ?: return SseDelta()
                    val text = buildString {
                        for (i in 0 until parts.length()) {
                            append(parts.optJSONObject(i)?.optString("text", "").orEmpty())
                        }
                    }
                    SseDelta(textDelta = text)
                }
            }
        } catch (_: Exception) {
            SseDelta()
        }
    }

    private fun buildHistoryMessageText(msg: ChatMessage): String = buildString {
        if (msg.content.isNotBlank()) append(msg.content)
        if (msg.hasArtifact()) {
            append("\n[Artifact ${msg.artifactTitle} (${msg.artifactLanguage})]:\n")
            append(msg.artifactContent)
        }
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun extractApiError(rawBody: String): String {
        if (rawBody.isBlank()) return "Request failed"
        return try {
            val json = JSONObject(rawBody)
            json.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: rawBody
        } catch (_: Exception) {
            rawBody
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
