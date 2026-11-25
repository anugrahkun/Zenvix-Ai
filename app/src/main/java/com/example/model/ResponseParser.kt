package com.example.model

data class ParsedAiResponse(
    val thinkingSummary: String?,
    val visibleText: String,
    val artifact: ExtractedArtifact?
)

object ResponseParser {

    private val THINKING_FULL_REGEX = Regex("<thinking>([\\s\\S]*?)</thinking>", RegexOption.IGNORE_CASE)
    private val THINKING_OPEN_REGEX = Regex("<thinking>([\\s\\S]*)$", RegexOption.IGNORE_CASE)

    private val ARTIFACT_FULL_REGEX = Regex(
        """<artifact\s+([^>]*)>([\s\S]*?)</artifact>""",
        RegexOption.IGNORE_CASE
    )
    private val ARTIFACT_OPEN_REGEX = Regex(
        """<artifact\s+([^>]*)>([\s\S]*)$""",
        RegexOption.IGNORE_CASE
    )
    private val ATTR_TITLE_REGEX = Regex("""title\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ATTR_LANG_REGEX = Regex("""language\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val FENCED_BLOCK_REGEX = Regex("""```([a-zA-Z0-9_+-]*)\n([\s\S]*?)```""")

    fun parse(raw: String, isFinal: Boolean): ParsedAiResponse {
        var working = raw
        var thinking: String? = null

        val fullThink = THINKING_FULL_REGEX.find(working)
        if (fullThink != null) {
            thinking = fullThink.groupValues[1].trim().takeIf { it.isNotEmpty() }
            working = working.removeRange(fullThink.range).trimStart()
        } else {
            val openThink = THINKING_OPEN_REGEX.find(working)
            if (openThink != null) {
                thinking = openThink.groupValues[1].trim().takeIf { it.isNotEmpty() }
                working = working.substring(0, openThink.range.first).trim()
            }
        }

        var artifact: ExtractedArtifact? = null
        val fullArtifact = ARTIFACT_FULL_REGEX.find(working)
        if (fullArtifact != null) {
            val attrs = fullArtifact.groupValues[1]
            val body = fullArtifact.groupValues[2].trim()
            val title = ATTR_TITLE_REGEX.find(attrs)?.groupValues?.get(1)?.trim() ?: "Generated Artifact"
            val lang = ATTR_LANG_REGEX.find(attrs)?.groupValues?.get(1)?.trim()?.lowercase() ?: "text"
            if (body.isNotEmpty()) {
                artifact = ExtractedArtifact(title = title, language = lang, content = body)
            }
            working = working.replaceRange(
                fullArtifact.range,
                "\n\n[Artifact ready: $title]\n"
            ).trim()
        } else if (!isFinal) {
            val openArtifact = ARTIFACT_OPEN_REGEX.find(working)
            if (openArtifact != null) {
                val attrs = openArtifact.groupValues[1]
                val body = openArtifact.groupValues[2].trimStart()
                val title = ATTR_TITLE_REGEX.find(attrs)?.groupValues?.get(1)?.trim() ?: "Building Artifact…"
                val lang = ATTR_LANG_REGEX.find(attrs)?.groupValues?.get(1)?.trim()?.lowercase() ?: "text"
                artifact = ExtractedArtifact(title = title, language = lang, content = body)
                working = working.substring(0, openArtifact.range.first).trim()
            }
        }

        // Fallback: if no explicit <artifact> tag was emitted, extract substantial fenced code block (>= 6 lines)
        if (artifact == null && isFinal) {
            val blocks = FENCED_BLOCK_REGEX.findAll(working).toList()
            val substantial = blocks.maxByOrNull { it.groupValues[2].lines().size }
            if (substantial != null && substantial.groupValues[2].lines().size >= 6) {
                val lang = substantial.groupValues[1].ifBlank { "code" }.lowercase()
                val code = substantial.groupValues[2].trim()
                val title = when (lang) {
                    "html" -> "Interactive Web Preview"
                    "svg" -> "Vector Graphic"
                    "kotlin", "kt" -> "Kotlin Module"
                    "python", "py" -> "Python Script"
                    "javascript", "js", "ts", "typescript" -> "JavaScript Component"
                    "json" -> "Structured JSON"
                    "sql" -> "SQL Schema / Query"
                    "markdown", "md" -> "Document Draft"
                    else -> "${lang.replaceFirstChar { it.uppercase() }} Artifact"
                }
                artifact = ExtractedArtifact(title = title, language = lang, content = code)
            }
        }

        return ParsedAiResponse(
            thinkingSummary = thinking,
            visibleText = working.trim(),
            artifact = artifact
        )
    }
}
