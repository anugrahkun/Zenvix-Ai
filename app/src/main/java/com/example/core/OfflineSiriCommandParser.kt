package com.example.core

import java.util.Locale

sealed interface OfflineSiriIntent {
    data object OpenWhatsApp : OfflineSiriIntent
    data class OpenApp(val appName: String) : OfflineSiriIntent
    data class OpenUrl(val url: String) : OfflineSiriIntent
    data class MakeCall(val phoneNumber: String) : OfflineSiriIntent
    data class CallContact(val contactName: String) : OfflineSiriIntent
    data class SetFlashlight(val enabled: Boolean) : OfflineSiriIntent
    data class OpenSystemPanel(val panelType: String) : OfflineSiriIntent
    data class AdjustVolume(val mode: String) : OfflineSiriIntent
    data class SetAlarm(val hour24: Int, val minute: Int, val label: String?) : OfflineSiriIntent
    data class SetTimer(val seconds: Int, val label: String?) : OfflineSiriIntent
    data object CheckBattery : OfflineSiriIntent
    data class CheckDateTime(val wantDate: Boolean) : OfflineSiriIntent
    data object AskCreator : OfflineSiriIntent
}

object OfflineSiriCommandParser {

    private val creatorKeywords = listOf(
        "who created you", "who made you", "who built you", "who is your creator",
        "who created zenvix", "who made zenvix", "who built zenvix",
        "tumko kaun banaya", "tumhe kisne banaya", "kisne create kiya",
        "kaun create kiya", "kisne banaya", "zenvix ai ko kisne", "zenvix ko kisne",
        "tumhara malik kaun", "tumhara creator kaun"
    )

    private val wakePrefixes = listOf(
        "hey zenvix", "hi zenvix", "ok zenvix", "okay zenvix", "hello zenvix",
        "zenvix please", "please", "zenvix", "hey siri", "siri"
    )

    fun parse(rawInput: String): OfflineSiriIntent? {
        var text = rawInput.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return null

        var lower = text.lowercase(Locale.US)
            .trimEnd('.', '!', '?', '।')
            .trim()

        for (prefix in wakePrefixes) {
            if (lower.startsWith("$prefix ")) {
                lower = lower.removePrefix("$prefix ").trim()
                text = text.drop(prefix.length).trim()
                break
            } else if (lower.startsWith("$prefix,")) {
                lower = lower.removePrefix("$prefix,").trim()
                text = text.drop(prefix.length + 1).trim()
                break
            }
        }
        if (lower.isEmpty()) return null

        // 1. Creator identity
        if (creatorKeywords.any { lower.contains(it) }) {
            return OfflineSiriIntent.AskCreator
        }

        // 2. Flashlight / Torch
        if (lower.contains("torch") || lower.contains("flashlight") || lower.contains("flash light") || lower == "flash on" || lower == "flash off") {
            val turnOff = lower.contains("off") || lower.contains("band") || lower.contains("close") || lower.contains("disable") || lower.contains("stop")
            return OfflineSiriIntent.SetFlashlight(enabled = !turnOff)
        }

        // 3. Battery status
        if (lower.contains("battery") || lower.contains("charge kitn") || lower.contains("charging kitn")) {
            return OfflineSiriIntent.CheckBattery
        }

        // 4. Time & Date
        if (lower.contains("what time") || lower.contains("current time") ||
            lower.contains("time kya") || lower.contains("kitne baje") || lower.contains("samay kya") ||
            lower == "time"
        ) {
            return OfflineSiriIntent.CheckDateTime(wantDate = false)
        }
        if (lower.contains("what date") || lower.contains("today's date") || lower.contains("todays date") ||
            lower.contains("tarikh") || lower.contains("tareekh") || lower.contains("aaj ki date") ||
            lower == "date"
        ) {
            return OfflineSiriIntent.CheckDateTime(wantDate = true)
        }

        // 5. Volume controls
        if (lower.contains("volume") || lower.contains("awaz") || lower.contains("awaaz") || lower.contains("sound level")) {
            return when {
                lower.contains("mute") || lower.contains("silent") || lower.contains("band") ->
                    OfflineSiriIntent.AdjustVolume("mute")
                lower.contains("unmute") ->
                    OfflineSiriIntent.AdjustVolume("unmute")
                lower.contains("max") || lower.contains("full") ->
                    OfflineSiriIntent.AdjustVolume("max")
                lower.contains("down") || lower.contains("kam") || lower.contains("slow") || lower.contains("decrease") || lower.contains("lower") ->
                    OfflineSiriIntent.AdjustVolume("down")
                else ->
                    OfflineSiriIntent.AdjustVolume("up")
            }
        }
        if (lower.contains("mute phone") || lower.contains("phone mute") || lower.contains("silent mode")) {
            return OfflineSiriIntent.AdjustVolume("mute")
        }

        // 6. System Settings panels (Wi-Fi, Bluetooth, Internet, Sound, Location, Display, Battery)
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            return OfflineSiriIntent.OpenSystemPanel("wifi")
        }
        if (lower.contains("bluetooth")) {
            return OfflineSiriIntent.OpenSystemPanel("bluetooth")
        }
        if (lower.contains("mobile data") || lower.contains("internet setting") || lower.contains("hotspot") || lower.contains("airplane")) {
            return OfflineSiriIntent.OpenSystemPanel("internet")
        }
        if (lower.contains("sound setting") || lower.contains("ringtone setting")) {
            return OfflineSiriIntent.OpenSystemPanel("sound")
        }
        if (lower.contains("brightness") || lower.contains("display setting")) {
            return OfflineSiriIntent.OpenSystemPanel("display")
        }
        if (lower.contains("location setting") || lower.contains("gps setting")) {
            return OfflineSiriIntent.OpenSystemPanel("location")
        }

        // 7. Timer
        if (lower.contains("timer")) {
            val seconds = parseTimerSeconds(lower)
            if (seconds != null && seconds > 0) {
                return OfflineSiriIntent.SetTimer(seconds = seconds, label = "Zenvix Timer")
            }
        }

        // 8. Alarm
        if (lower.contains("alarm")) {
            val parsed = parseAlarmHourMinute(lower)
            if (parsed != null) {
                return OfflineSiriIntent.SetAlarm(hour24 = parsed.first, minute = parsed.second, label = "Zenvix Alarm")
            }
        }

        // 9. WhatsApp shortcuts
        if (lower.contains("whatsapp") &&
            (lower.contains("open") || lower.contains("khol") || lower.contains("chala") || lower.contains("launch") || lower.contains("start") || lower == "whatsapp")
        ) {
            return OfflineSiriIntent.OpenWhatsApp
        }

        // 10. Phone Call (number or contact name)
        val callResult = parseCallCommand(lower, text)
        if (callResult != null) return callResult

        // 11. Explicit URL
        val urlRegex = Regex("(https?://[^\\s]+|[a-zA-Z0-9-]+\\.(com|org|in|net|io|ai|co)(/[^\\s]*)?)", RegexOption.IGNORE_CASE)
        if (lower.startsWith("open ") || lower.startsWith("visit ") || lower.contains("khol")) {
            urlRegex.find(text)?.let { match ->
                DeviceActionValidator.sanitizeSafeHttpUrl(match.value)?.let { safe ->
                    return OfflineSiriIntent.OpenUrl(safe)
                }
            }
        }

        // 12. Open App commands ("open youtube", "youtube kholo", "instagram chalao", "open settings")
        val appTarget = extractAppName(lower)
        if (!appTarget.isNullOrBlank()) {
            return if (appTarget.contains("whatsapp")) {
                OfflineSiriIntent.OpenWhatsApp
            } else {
                OfflineSiriIntent.OpenApp(appTarget)
            }
        }

        return null
    }

    private fun parseCallCommand(lower: String, original: String): OfflineSiriIntent? {
        val isCallIntent = lower.startsWith("call ") ||
            lower.contains(" ko call") ||
            lower.contains(" ko phone") ||
            lower.contains(" pe call") ||
            lower.contains(" par call") ||
            lower.contains("make a call") ||
            lower.contains("dial ") ||
            lower.contains("phone lagao") ||
            lower.contains("call karo") ||
            lower.contains("call lagao")

        if (!isCallIntent) return null

        // Check if phone number supplied
        val numMatch = Regex("(\\+?[0-9][0-9\\s\\-()]{2,20}[0-9])").find(original)
        if (numMatch != null) {
            val normalized = DeviceActionValidator.normalizePhoneNumber(numMatch.value)
            if (normalized != null) {
                return OfflineSiriIntent.MakeCall(normalized)
            }
        }

        // Otherwise extract contact name cleanly
        var name = lower
        val stripPatterns = listOf(
            Regex("^(please\\s+)?(make a )?call(\\s+to)?\\s+"),
            Regex("^dial\\s+"),
            Regex("\\s+(ko|ke|pe|par)?\\s*(call|phone)\\s*(karo|karna|lagao|laga|do|kardo|kar do)$"),
            Regex("\\s+(ko|pe|par)$"),
            Regex("^my\\s+")
        )
        stripPatterns.forEach { regex ->
            name = name.replace(regex, " ").trim()
        }
        name = name.replace(Regex("\\s+"), " ").trim()
        return if (name.isNotBlank()) {
            OfflineSiriIntent.CallContact(name)
        } else {
            null
        }
    }

    private fun extractAppName(lower: String): String? {
        val openPrefixes = listOf("open my ", "open the ", "open ", "launch ", "start ", "chalu karo ", "kholo ")
        for (p in openPrefixes) {
            if (lower.startsWith(p)) {
                val candidate = lower.removePrefix(p)
                    .removeSuffix(" app")
                    .trim()
                if (candidate.isNotBlank()) return candidate
            }
        }
        val hindiSuffixes = listOf(" app kholo", " kholo", " open karo", " chalao", " chalu karo", " start karo")
        for (s in hindiSuffixes) {
            if (lower.endsWith(s)) {
                val candidate = lower.removeSuffix(s)
                    .removePrefix("my ")
                    .trim()
                if (candidate.isNotBlank()) return candidate
            }
        }
        return null
    }

    private fun parseTimerSeconds(lower: String): Int? {
        val match = Regex("(\\d+)\\s*(hour|hours|hr|hrs|ghanta|ghante|minute|minutes|min|mins|second|seconds|sec|secs)?").find(lower)
            ?: return null
        val value = match.groupValues[1].toIntOrNull() ?: return null
        val unit = match.groupValues.getOrNull(2).orEmpty()
        return when {
            unit.startsWith("h") || unit.startsWith("g") -> value * 3600
            unit.startsWith("s") -> value
            else -> value * 60
        }
    }

    private fun parseAlarmHourMinute(lower: String): Pair<Int, Int>? {
        val timeRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)?")
        val match = timeRegex.find(lower) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3]
        if (hour !in 0..23 || minute !in 0..59) return null
        if (meridiem == "pm" && hour in 1..11) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        return hour to minute
    }
}
