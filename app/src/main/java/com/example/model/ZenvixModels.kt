package com.example.model

import android.graphics.Bitmap
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ApiProvider(
    val label: String,
    val defaultBaseUrl: String,
    val hint: String
) {
    OPENAI_COMPATIBLE(
        label = "OpenAI / OpenRouter / Custom",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        hint = "Uses Authorization: Bearer <key> at /chat/completions"
    ),
    ANTHROPIC(
        label = "Anthropic Claude API",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        hint = "Uses x-api-key at /messages"
    ),
    GEMINI_REST(
        label = "Gemini REST API",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        hint = "Uses x-goog-api-key at /models/{model}:streamGenerateContent"
    );

    companion object {
        fun fromRaw(raw: String?): ApiProvider =
            entries.firstOrNull { it.name == raw } ?: OPENAI_COMPATIBLE
    }
}

data class ZenvixModelSpec(
    val id: String,
    val displayName: String,
    val badge: String,
    val subtitle: String,
    val isExternalApi: Boolean,
    val isFreeBuiltIn: Boolean
)

data class CustomApiSettings(
    val provider: ApiProvider = ApiProvider.OPENAI_COMPATIBLE,
    val baseUrl: String = ApiProvider.OPENAI_COMPATIBLE.defaultBaseUrl,
    val apiKey: String = "",
    val customModels: List<ZenvixModelSpec> = emptyList()
) {
    fun hasConfiguredKey(): Boolean = apiKey.isNotBlank()
}

enum class ZenvixModelOption(
    val modelId: String,
    val displayName: String,
    val badge: String,
    val subtitle: String,
    val isFreeTier: Boolean
) {
    SONNET_FLASH(
        modelId = "gemini-3.8-flash",
        displayName = "Zenvix Sonnet 3.8",
        badge = "Free • Frontier",
        subtitle = "Balanced reasoning, code & artifacts, vision & docs",
        isFreeTier = true
    ),
    HAIKU_LITE(
        modelId = "gemini-3.5-flash-lite",
        displayName = "Zenvix Haiku 3.5",
        badge = "Free • Ultra-Fast",
        subtitle = "Instant replies, quick summaries & translations",
        isFreeTier = true
    ),
    OPUS_ARGON(
        modelId = "gemini-4-argon",
        displayName = "Zenvix Opus 4",
        badge = "Blaze Plan",
        subtitle = "Deep STEM, math & complex system architecture",
        isFreeTier = false
    );

    fun toSpec(): ZenvixModelSpec = ZenvixModelSpec(
        id = modelId,
        displayName = displayName,
        badge = badge,
        subtitle = subtitle,
        isExternalApi = false,
        isFreeBuiltIn = isFreeTier
    )

    companion object {
        fun builtInSpecs(): List<ZenvixModelSpec> = entries.map { it.toSpec() }

        fun resolveSpec(
            modelId: String?,
            externalModels: List<ZenvixModelSpec>
        ): ZenvixModelSpec {
            val target = modelId?.trim().orEmpty()
            builtInSpecs().firstOrNull { it.id == target }?.let { return it }
            externalModels.firstOrNull { it.id == target }?.let { return it }
            if (target.isNotEmpty()) {
                return ZenvixModelSpec(
                    id = target,
                    displayName = target,
                    badge = "API • Custom",
                    subtitle = "External API model",
                    isExternalApi = true,
                    isFreeBuiltIn = false
                )
            }
            return SONNET_FLASH.toSpec()
        }

        fun fromId(raw: String?): ZenvixModelOption =
            entries.firstOrNull { it.modelId == raw } ?: SONNET_FLASH
    }
}

enum class StylePreset(
    val label: String,
    val shortHint: String,
    val instruction: String
) {
    BALANCED(
        label = "Balanced",
        shortHint = "Warm, clear & thoughtful",
        instruction = "Respond with clear, well-structured, thoughtful prose like a helpful senior partner."
    ),
    CONCISE(
        label = "Concise",
        shortHint = "Direct, crisp & zero fluff",
        instruction = "Be maximally concise and direct. Prefer crisp bullet points and immediate answers without filler."
    ),
    EXPLANATORY(
        label = "Explanatory",
        shortHint = "Step-by-step teaching",
        instruction = "Teach concepts step-by-step using intuitive analogies, concrete examples, and clear takeaways."
    ),
    FORMAL(
        label = "Formal",
        shortHint = "Polished executive tone",
        instruction = "Write in polished, formal executive prose suitable for memos, reports, and professional briefs."
    ),
    CODE_ARCHITECT(
        label = "Code Architect",
        shortHint = "Production code & clean specs",
        instruction = "Prioritize production-ready, modular code, edge-case handling, and clean architectural trade-offs."
    );

    companion object {
        fun fromRaw(raw: String?): StylePreset =
            entries.firstOrNull { it.name == raw } ?: BALANCED
    }
}

@Keep
@IgnoreExtraProperties
data class AiProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val stylePreset: String = StylePreset.BALANCED.name,
    val accentIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedStyle(): StylePreset = StylePreset.fromRaw(stylePreset)
}

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New conversation",
    val projectId: String? = null,
    val modelId: String = ZenvixModelOption.SONNET_FLASH.modelId,
    val stylePreset: String = StylePreset.BALANCED.name,
    val thinkingEnabled: Boolean = false,
    val pinned: Boolean = false,
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedModel(): ZenvixModelOption = ZenvixModelOption.fromId(modelId)
    fun resolvedStyle(): StylePreset = StylePreset.fromRaw(stylePreset)
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatId: String = "",
    val role: String = "user",
    val content: String = "",
    val thinkingSummary: String? = null,
    val attachmentName: String? = null,
    val artifactTitle: String? = null,
    val artifactLanguage: String? = null,
    val artifactContent: String? = null,
    val modelId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun hasArtifact(): Boolean =
        !artifactTitle.isNullOrBlank() && !artifactContent.isNullOrBlank()

    fun toArtifactOrNull(): ExtractedArtifact? {
        val title = artifactTitle?.takeIf { it.isNotBlank() } ?: return null
        val content = artifactContent?.takeIf { it.isNotBlank() } ?: return null
        val language = artifactLanguage?.takeIf { it.isNotBlank() } ?: "text"
        return ExtractedArtifact(title = title, language = language, content = content)
    }
}

@Keep
@IgnoreExtraProperties
data class SavedArtifact(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val language: String = "text",
    val content: String = "",
    val chatId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserApp(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val htmlCode: String = "",
    val lastPrompt: String = "",
    val modelId: String = ZenvixModelOption.SONNET_FLASH.modelId,
    val version: Long = 1L,
    val accentIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

const val ZENVIX_OWNER_EMAIL = "anugrah.business.2.3@gmail.com"

fun isVerifiedOwnerAccount(email: String?, isEmailVerified: Boolean): Boolean =
    isEmailVerified && email?.trim()?.equals(ZENVIX_OWNER_EMAIL, ignoreCase = true) == true

enum class SessionStatus(val label: String) {
    SIGNED_IN("Signed In"),
    SIGNED_OUT("Signed Out");

    companion object {
        fun fromRaw(raw: String?): SessionStatus =
            entries.firstOrNull { it.name == raw } ?: SIGNED_IN
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val lastModelId: String = ZenvixModelOption.SONNET_FLASH.modelId,
    val chatCount: Long = 0L,
    val appCount: Long = 0L,
    val sessionStatus: String = SessionStatus.SIGNED_IN.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val lastActiveAt: Timestamp? = null
) {
    fun resolvedSessionStatus(): SessionStatus = SessionStatus.fromRaw(sessionStatus)
}

enum class AuthEventType(val label: String) {
    LOGIN("Logged In"),
    LOGOUT("Logged Out");

    companion object {
        fun fromRaw(raw: String?): AuthEventType =
            entries.firstOrNull { it.name == raw } ?: LOGIN
    }
}

@Keep
@IgnoreExtraProperties
data class AuthEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val eventType: String = AuthEventType.LOGIN.name,
    val deviceInfo: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun resolvedEventType(): AuthEventType = AuthEventType.fromRaw(eventType)
}

enum class BugCategory(val label: String) {
    AI_CHAT("AI Chat"),
    YOUR_APP("Your App Builder"),
    API_KEYS("API Keys & Models"),
    LOGIN_SYNC("Google Login & Sync"),
    UI_ANIMATION("UI & Animations"),
    OTHER("Other Feedback");

    companion object {
        fun fromRaw(raw: String?): BugCategory =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class BugStatus(val label: String) {
    OPEN("Open"),
    IN_PROGRESS("In Progress"),
    FIXED("Fixed");

    companion object {
        fun fromRaw(raw: String?): BugStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

@Keep
@IgnoreExtraProperties
data class BugReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val reporterName: String = "",
    val reporterEmail: String = "",
    val category: String = BugCategory.OTHER.name,
    val title: String = "",
    val description: String = "",
    val diagnostics: String = "",
    val status: String = BugStatus.OPEN.name,
    val ownerNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedCategory(): BugCategory = BugCategory.fromRaw(category)
    fun resolvedStatus(): BugStatus = BugStatus.fromRaw(status)
}

data class ExtractedArtifact(
    val title: String,
    val language: String,
    val content: String
) {
    fun supportsLivePreview(): Boolean {
        val normalized = language.lowercase()
        return normalized == "html" || normalized == "svg" || normalized == "markdown" || normalized == "md"
    }
}

sealed interface PendingAttachment {
    val displayName: String

    data class ImageAttachment(
        override val displayName: String,
        val bitmap: Bitmap
    ) : PendingAttachment

    data class DocumentAttachment(
        override val displayName: String,
        val mimeType: String,
        val bytes: ByteArray,
        val extractedText: String? = null
    ) : PendingAttachment
}
