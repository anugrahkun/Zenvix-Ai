package com.example.data

import android.util.Log
import com.example.model.AiProject
import com.example.model.ChatMessage
import com.example.model.CustomApiSettings
import com.example.model.ParsedAiResponse
import com.example.model.PendingAttachment
import com.example.model.ResponseParser
import com.example.model.StylePreset
import com.example.model.ZenvixModelOption
import com.example.model.ZenvixModelSpec
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "ZenvixAiService"
private const val STREAM_TIMEOUT_MS = 60_000L
private const val TITLE_TIMEOUT_MS = 20_000L

class ZenvixAiService(
    private val externalClient: ExternalApiAiClient = ExternalApiAiClient()
) {

    private val titleModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ZenvixModelOption.HAIKU_LITE.modelId,
            generationConfig = generationConfig {
                temperature = 0.3f
                maxOutputTokens = 32
            }
        )
    }

    private fun buildSystemInstruction(
        stylePreset: StylePreset,
        thinkingEnabled: Boolean,
        activeProject: AiProject?
    ): String = buildString {
        appendLine("You are Zenvix AI, a calm, deeply capable, editorial cloud AI assistant created by Anugrah Das.")
        appendLine("Creator identity rule: Whenever anyone asks who created, built, or made Zenvix AI or you (in English, Hindi, Hinglish, or any other language), always clearly answer that you (Zenvix AI) were created by Anugrah Das.")
        appendLine("Style preference: ${stylePreset.instruction}")
        if (activeProject != null && activeProject.systemPrompt.isNotBlank()) {
            appendLine()
            appendLine("Active Project (${activeProject.name}) instructions:")
            appendLine(activeProject.systemPrompt.trim())
        }
        if (thinkingEnabled) {
            appendLine()
            appendLine(
                "Extended Thinking is enabled. Before giving your final answer, write a concise, " +
                    "structured step-by-step reasoning trace wrapped inside <thinking>...</thinking> tags. " +
                    "Then provide your clear final response outside the tags."
            )
        }
        appendLine()
        appendLine(
            "Artifacts Studio capability: When the user asks you to write a standalone app, webpage, " +
                "HTML/CSS/JS widget, SVG illustration, substantial code file, or formatted document draft, " +
                "wrap that deliverable inside <artifact title=\"Short Descriptive Title\" language=\"html|svg|kotlin|python|markdown|json|text\">...</artifact> " +
                "and summarize what you built outside the tag."
        )
    }

    private fun createBuiltInModel(
        modelId: String,
        stylePreset: StylePreset,
        systemText: String
    ): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = modelId,
            generationConfig = generationConfig {
                temperature = if (stylePreset == StylePreset.CODE_ARCHITECT) 0.25f else 0.7f
            },
            systemInstruction = content { text(systemText) }
        )
    }

    fun streamReply(
        historyMessages: List<ChatMessage>,
        userPrompt: String,
        attachment: PendingAttachment?,
        modelSpec: ZenvixModelSpec,
        customApiSettings: CustomApiSettings,
        stylePreset: StylePreset,
        thinkingEnabled: Boolean,
        activeProject: AiProject?
    ): Flow<ParsedAiResponse> {
        val systemText = buildSystemInstruction(
            stylePreset = stylePreset,
            thinkingEnabled = thinkingEnabled,
            activeProject = activeProject
        )

        if (modelSpec.isExternalApi) {
            return externalClient.streamReply(
                settings = customApiSettings,
                modelId = modelSpec.id,
                systemInstruction = systemText,
                historyMessages = historyMessages,
                userPrompt = userPrompt,
                attachment = attachment,
                stylePreset = stylePreset
            )
        }

        return flow {
            val model = createBuiltInModel(
                modelId = modelSpec.id,
                stylePreset = stylePreset,
                systemText = systemText
            )

            val sdkHistory: List<Content> = historyMessages
                .takeLast(18)
                .filter { it.content.isNotBlank() || it.hasArtifact() }
                .map { msg ->
                    val fullText = buildString {
                        if (msg.content.isNotBlank()) append(msg.content)
                        if (msg.hasArtifact()) {
                            append("\n[Artifact ${msg.artifactTitle} (${msg.artifactLanguage})]:\n")
                            append(msg.artifactContent)
                        }
                    }
                    content(role = if (msg.role == "user") "user" else "model") {
                        text(fullText)
                    }
                }

            val chat = model.startChat(history = sdkHistory)
            val outgoingContent = content("user") {
                when (attachment) {
                    is PendingAttachment.ImageAttachment -> {
                        image(attachment.bitmap)
                    }
                    is PendingAttachment.DocumentAttachment -> {
                        if (!attachment.extractedText.isNullOrBlank()) {
                            text("Attached file (${attachment.displayName}):\n${attachment.extractedText}")
                        } else {
                            inlineData(attachment.bytes, attachment.mimeType)
                        }
                    }
                    null -> Unit
                }
                val effectivePrompt = userPrompt.ifBlank {
                    when (attachment) {
                        is PendingAttachment.ImageAttachment -> "Analyze this image in detail."
                        is PendingAttachment.DocumentAttachment -> "Analyze and summarize this file."
                        null -> ""
                    }
                }
                text(effectivePrompt)
            }

            val accumulated = StringBuilder()
            try {
                withTimeout(STREAM_TIMEOUT_MS) {
                    chat.sendMessageStream(outgoingContent).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            emit(ResponseParser.parse(accumulated.toString(), isFinal = false))
                        }
                    }
                }
                if (accumulated.isBlank()) {
                    throw IllegalStateException("The cloud model returned an empty response.")
                }
                emit(ResponseParser.parse(accumulated.toString(), isFinal = true))
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Streaming timed out after ${STREAM_TIMEOUT_MS}ms", e)
                throw IllegalStateException(
                    "Cloud AI request timed out after 60 seconds. Try switching to Zenvix Haiku 3.5 or shortening the prompt.",
                    e
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI Logic generation error", e)
                throw e
            }
        }
    }

    fun streamBuildOrUpdateApp(
        appName: String,
        appPrompt: String,
        existingHtmlCode: String?,
        modelSpec: ZenvixModelSpec,
        customApiSettings: CustomApiSettings
    ): Flow<ParsedAiResponse> {
        val builderSystem = buildString {
            appendLine("You are Zenvix App Studio, an expert mobile-first HTML5/CSS3/JS application engineer.")
            appendLine("Build complete, self-contained, single-file interactive web apps that run smoothly inside an Android WebView.")
            appendLine("Design rules: modern responsive mobile layout, smooth CSS transitions/animations, zero external CDN dependencies (write all CSS and JS inline), localStorage persistence where helpful, crisp typography, accessible tap targets.")
            appendLine("Optional Android device bridge: when an app explicitly needs phone/device actions, detect `window.ZenvixAndroid` (supports `openWhatsApp()`, `openApp(appName)`, `openUrl(url)`, `makeCall(phoneNumber)`, `callContact(contactName)`, `setFlashlight(enabled)`, `openSystemPanel(panelType)`, `adjustVolume(mode)`, `executeDeviceCommand(commandText)`, `isOnline()`) and fall back gracefully to `tel:` or `https://` links in standard browsers.")
            appendLine("Output rule: Return the COMPLETE runnable HTML document wrapped inside <artifact title=\"$appName\" language=\"html\">...</artifact> tags, followed by a brief 1-sentence summary of what was built or updated.")
        }

        val fullPrompt = if (existingHtmlCode.isNullOrBlank()) {
            "Create a complete, polished interactive app named \"$appName\".\nRequirements:\n$appPrompt"
        } else {
            "Update the existing app named \"$appName\" according to the user's change request.\n" +
                "Preserve existing working features unless asked to replace them, and return the COMPLETE updated HTML inside <artifact title=\"$appName\" language=\"html\">...</artifact>.\n\n" +
                "Requested changes:\n$appPrompt\n\n" +
                "Current HTML source:\n```html\n${existingHtmlCode.take(60_000)}\n```"
        }

        if (modelSpec.isExternalApi) {
            return externalClient.streamReply(
                settings = customApiSettings,
                modelId = modelSpec.id,
                systemInstruction = builderSystem,
                historyMessages = emptyList(),
                userPrompt = fullPrompt,
                attachment = null,
                stylePreset = StylePreset.CODE_ARCHITECT
            )
        }

        return flow {
            val model = createBuiltInModel(
                modelId = modelSpec.id,
                stylePreset = StylePreset.CODE_ARCHITECT,
                systemText = builderSystem
            )
            val outgoing = content("user") { text(fullPrompt) }
            val accumulated = StringBuilder()
            try {
                withTimeout(STREAM_TIMEOUT_MS) {
                    model.generateContentStream(outgoing).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            emit(ResponseParser.parse(accumulated.toString(), isFinal = false))
                        }
                    }
                }
                if (accumulated.isBlank()) {
                    throw IllegalStateException("Cloud model returned an empty app build response.")
                }
                emit(ResponseParser.parse(accumulated.toString(), isFinal = true))
            } catch (e: TimeoutCancellationException) {
                throw IllegalStateException("App build timed out after 60 seconds.", e)
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    suspend fun testExternalConnection(
        settings: CustomApiSettings,
        modelId: String
    ): Result<String> = externalClient.testConnection(settings, modelId)

    suspend fun generateChatTitle(firstPrompt: String): String {
        val fallback = firstPrompt
            .trim()
            .replace("\n", " ")
            .take(44)
            .ifBlank { "New conversation" }
        return try {
            withTimeout(TITLE_TIMEOUT_MS) {
                val response = titleModel.generateContent(
                    "Summarize this user request into a concise 3 to 5 word chat title with no quotes or punctuation:\n$firstPrompt"
                )
                response.text
                    ?.trim()
                    ?.replace("\"", "")
                    ?.take(60)
                    ?.takeIf { it.isNotBlank() }
                    ?: fallback
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Falling back to truncated title: ${e.message}")
            fallback
        }
    }
}
