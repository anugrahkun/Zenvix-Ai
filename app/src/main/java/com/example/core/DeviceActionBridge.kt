package com.example.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ContactCandidate(
    val displayName: String,
    val phoneNumber: String
)

enum class DeviceActionStatus {
    SUCCESS,
    OPENED_DIALER_PREFILLED,
    NOT_INSTALLED,
    NOT_FOUND,
    AMBIGUOUS,
    PERMISSION_REQUIRED,
    INVALID_INPUT,
    FAILED
}

data class DeviceActionOutcome(
    val actionName: String,
    val executed: Boolean,
    val status: DeviceActionStatus,
    val message: String,
    val missingPermission: String? = null,
    val candidates: List<ContactCandidate> = emptyList(),
    val targetLabel: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("action", actionName)
        put("executed", executed)
        put("status", status.name.lowercase(Locale.US))
        put("message", message)
        if (!missingPermission.isNullOrBlank()) {
            put("missingPermission", missingPermission)
        }
        if (!targetLabel.isNullOrBlank()) {
            put("target", targetLabel)
        }
        if (candidates.isNotEmpty()) {
            val arr = JSONArray()
            candidates.forEach { c ->
                arr.put(
                    JSONObject().apply {
                        put("name", c.displayName)
                        put("phoneNumber", c.phoneNumber)
                    }
                )
            }
            put("candidates", arr)
        }
    }
}

sealed interface ContactLookupResult {
    data object NotFound : ContactLookupResult
    data class ExactMatch(val candidate: ContactCandidate) : ContactLookupResult
    data class Ambiguous(val candidates: List<ContactCandidate>) : ContactLookupResult
}

object DeviceActionValidator {

    fun normalizePhoneNumber(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val sb = StringBuilder()
        trimmed.forEachIndexed { idx, ch ->
            when {
                ch.isDigit() -> sb.append(ch)
                ch == '+' && sb.isEmpty() -> sb.append('+')
                (ch == '*' || ch == '#') -> sb.append(ch)
                idx > 0 && (ch == '-' || ch == ' ' || ch == '(' || ch == ')') -> Unit
            }
        }
        val normalized = sb.toString()
        val digitCount = normalized.count { it.isDigit() }
        return if (digitCount >= 3 && normalized.length <= 24) normalized else null
    }

    fun sanitizeSafeHttpUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty() || trimmed.length > 2048) return null
        val lower = trimmed.lowercase(Locale.US)
        if (lower.startsWith("javascript:") ||
            lower.startsWith("file:") ||
            lower.startsWith("content:") ||
            lower.startsWith("data:") ||
            lower.startsWith("intent:")
        ) {
            return null
        }
        val withScheme = when {
            lower.startsWith("https://") || lower.startsWith("http://") -> trimmed
            !trimmed.contains("://") && !trimmed.contains(":") && trimmed.contains('.') -> "https://$trimmed"
            else -> return null
        }
        return try {
            val parsed = java.net.URI(withScheme)
            val scheme = parsed.scheme?.lowercase(Locale.US)
            val host = parsed.host
            if ((scheme == "https" || scheme == "http") && !host.isNullOrBlank()) {
                withScheme
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun mapAppQueryToPackages(appName: String): List<String> {
        val q = appName.trim().lowercase(Locale.US)
        return when {
            q.contains("whatsapp") -> listOf("com.whatsapp", "com.whatsapp.w4b")
            q.contains("youtube") || q == "yt" -> listOf("com.google.android.youtube")
            q.contains("instagram") || q == "insta" || q == "ig" -> listOf("com.instagram.android")
            q.contains("chrome") || q.contains("browser") -> listOf("com.android.chrome")
            q.contains("map") -> listOf("com.google.android.apps.maps")
            q.contains("gmail") || q == "mail" -> listOf("com.google.android.gm")
            q.contains("play store") -> listOf("com.android.vending")
            q.contains("calendar") -> listOf("com.google.android.calendar")
            q.contains("message") || q == "sms" -> listOf("com.google.android.apps.messaging")
            q.contains("dialer") || q == "phone" -> listOf("com.google.android.dialer")
            else -> emptyList()
        }
    }

    fun isSettingsQuery(appName: String): Boolean {
        val q = appName.trim().lowercase(Locale.US)
        return q == "settings" || q == "setting" || q.contains("device settings") || q.contains("phone settings")
    }

    fun isCameraQuery(appName: String): Boolean {
        val q = appName.trim().lowercase(Locale.US)
        return q == "camera" || q.contains("open camera")
    }

    fun expandContactQueryTerms(rawName: String): List<String> {
        val clean = rawName.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return emptyList()
        val lower = clean.lowercase(Locale.US)
        val terms = linkedSetOf(lower)
        when (lower) {
            "mom", "mummy", "mamma", "mama", "mother", "maa", "ma", "ammi", "aai" -> {
                terms.addAll(listOf("mom", "mummy", "mother", "maa", "mama", "mamma", "ammi", "aai"))
            }
            "dad", "daddy", "papa", "father", "abba", "baba", "pa" -> {
                terms.addAll(listOf("dad", "daddy", "papa", "father", "abba", "baba"))
            }
            "bro", "brother", "bhai", "bhaiya" -> {
                terms.addAll(listOf("brother", "bhai", "bhaiya"))
            }
            "sis", "sister", "didi", "behen" -> {
                terms.addAll(listOf("sister", "didi", "behen"))
            }
        }
        return terms.toList()
    }

    fun evaluateContactMatches(
        rawQuery: String,
        candidates: List<ContactCandidate>
    ): ContactLookupResult {
        val terms = expandContactQueryTerms(rawQuery)
        if (terms.isEmpty() || candidates.isEmpty()) return ContactLookupResult.NotFound

        val distinctNormalized = candidates
            .mapNotNull { candidate ->
                val cleanName = candidate.displayName.trim()
                val cleanNum = normalizePhoneNumber(candidate.phoneNumber) ?: return@mapNotNull null
                ContactCandidate(cleanName.ifBlank { cleanNum }, cleanNum)
            }
            .distinctBy { "${it.displayName.lowercase(Locale.US)}|${it.phoneNumber.takeLast(10)}" }

        if (distinctNormalized.isEmpty()) return ContactLookupResult.NotFound

        val exactMatches = distinctNormalized.filter { cand ->
            val candLower = cand.displayName.lowercase(Locale.US)
            terms.any { term -> candLower == term }
        }
        val pool = if (exactMatches.isNotEmpty()) exactMatches else {
            distinctNormalized.filter { cand ->
                val candLower = cand.displayName.lowercase(Locale.US)
                val tokens = candLower.split(' ', '-', '_')
                terms.any { term ->
                    candLower.contains(term) || tokens.any { it.startsWith(term) }
                }
            }
        }

        if (pool.isEmpty()) return ContactLookupResult.NotFound

        val groupedByName = pool.groupBy { it.displayName.lowercase(Locale.US) }
        return if (groupedByName.size == 1) {
            ContactLookupResult.ExactMatch(pool.first())
        } else {
            ContactLookupResult.Ambiguous(pool.take(5))
        }
    }
}

class DeviceActionBridge(private val context: Context) {

    fun openWhatsApp(): DeviceActionOutcome {
        val pm = context.packageManager
        val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in whatsappPackages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null && startActivitySafely(launchIntent)) {
                return DeviceActionOutcome(
                    actionName = "openWhatsApp",
                    executed = true,
                    status = DeviceActionStatus.SUCCESS,
                    message = "Opened WhatsApp.",
                    targetLabel = "WhatsApp"
                )
            }
        }
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://app")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (deepLink.resolveActivity(pm) != null && startActivitySafely(deepLink)) {
            return DeviceActionOutcome(
                actionName = "openWhatsApp",
                executed = true,
                status = DeviceActionStatus.SUCCESS,
                message = "Opened WhatsApp.",
                targetLabel = "WhatsApp"
            )
        }
        return DeviceActionOutcome(
            actionName = "openWhatsApp",
            executed = false,
            status = DeviceActionStatus.NOT_INSTALLED,
            message = "WhatsApp is not installed on this device.",
            targetLabel = "WhatsApp"
        )
    }

    fun openApp(appName: String): DeviceActionOutcome {
        val cleanName = appName.trim()
        if (cleanName.isEmpty()) {
            return DeviceActionOutcome(
                actionName = "openApp",
                executed = false,
                status = DeviceActionStatus.INVALID_INPUT,
                message = "No app name was specified."
            )
        }

        if (cleanName.lowercase(Locale.US).contains("whatsapp")) {
            return openWhatsApp()
        }

        if (DeviceActionValidator.isSettingsQuery(cleanName)) {
            val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (startActivitySafely(settingsIntent)) {
                return DeviceActionOutcome(
                    actionName = "openApp",
                    executed = true,
                    status = DeviceActionStatus.SUCCESS,
                    message = "Opened device settings.",
                    targetLabel = "Settings"
                )
            }
        }

        if (DeviceActionValidator.isCameraQuery(cleanName)) {
            val cameraIntent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (startActivitySafely(cameraIntent)) {
                return DeviceActionOutcome(
                    actionName = "openApp",
                    executed = true,
                    status = DeviceActionStatus.SUCCESS,
                    message = "Opened Camera.",
                    targetLabel = "Camera"
                )
            }
        }

        val pm = context.packageManager
        val mappedPackages = DeviceActionValidator.mapAppQueryToPackages(cleanName)
        for (pkg in mappedPackages) {
            val intent = pm.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null && startActivitySafely(intent)) {
                return DeviceActionOutcome(
                    actionName = "openApp",
                    executed = true,
                    status = DeviceActionStatus.SUCCESS,
                    message = "Opened $cleanName.",
                    targetLabel = cleanName
                )
            }
        }

        // Fallback: query declared launcher apps matching label
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = runCatching { pm.queryIntentActivities(launcherIntent, 0) }.getOrDefault(emptyList())
        val lower = cleanName.lowercase(Locale.US)
        val matchedResolve = installed.firstOrNull { resolveInfo ->
            val label = resolveInfo.loadLabel(pm)?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
            label == lower || label.contains(lower)
        }
        if (matchedResolve != null) {
            val pkg = matchedResolve.activityInfo.packageName
            val launch = pm.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launch != null && startActivitySafely(launch)) {
                val realLabel = matchedResolve.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { cleanName }
                return DeviceActionOutcome(
                    actionName = "openApp",
                    executed = true,
                    status = DeviceActionStatus.SUCCESS,
                    message = "Opened $realLabel.",
                    targetLabel = realLabel
                )
            }
        }

        return DeviceActionOutcome(
            actionName = "openApp",
            executed = false,
            status = DeviceActionStatus.NOT_INSTALLED,
            message = "$cleanName is not installed or could not be opened on this device.",
            targetLabel = cleanName
        )
    }

    fun openUrl(url: String): DeviceActionOutcome {
        val safeUrl = DeviceActionValidator.sanitizeSafeHttpUrl(url)
            ?: return DeviceActionOutcome(
                actionName = "openUrl",
                executed = false,
                status = DeviceActionStatus.INVALID_INPUT,
                message = "Only safe http or https links are allowed."
            )
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (startActivitySafely(intent)) {
            DeviceActionOutcome(
                actionName = "openUrl",
                executed = true,
                status = DeviceActionStatus.SUCCESS,
                message = "Opened $safeUrl in browser.",
                targetLabel = safeUrl
            )
        } else {
            DeviceActionOutcome(
                actionName = "openUrl",
                executed = false,
                status = DeviceActionStatus.FAILED,
                message = "No browser app could open $safeUrl.",
                targetLabel = safeUrl
            )
        }
    }

    fun makeCall(phoneNumber: String, displayLabel: String? = null): DeviceActionOutcome {
        val normalized = DeviceActionValidator.normalizePhoneNumber(phoneNumber)
            ?: return DeviceActionOutcome(
                actionName = "makeCall",
                executed = false,
                status = DeviceActionStatus.INVALID_INPUT,
                message = "The phone number is missing or invalid."
            )

        val label = displayLabel?.takeIf { it.isNotBlank() } ?: normalized
        val telUri = Uri.fromParts("tel", normalized, null)
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCallPermission) {
            val callIntent = Intent(Intent.ACTION_CALL, telUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (startActivitySafely(callIntent)) {
                return DeviceActionOutcome(
                    actionName = "makeCall",
                    executed = true,
                    status = DeviceActionStatus.SUCCESS,
                    message = "Calling $label ($normalized).",
                    targetLabel = label
                )
            }
        }

        // Safe dialer fallback when direct CALL_PHONE is not granted or unavailable
        val dialIntent = Intent(Intent.ACTION_DIAL, telUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (startActivitySafely(dialIntent)) {
            DeviceActionOutcome(
                actionName = "makeCall",
                executed = true,
                status = DeviceActionStatus.OPENED_DIALER_PREFILLED,
                message = "Opened the phone dialer pre-filled with $label ($normalized) because direct call permission is not enabled yet.",
                missingPermission = if (!hasCallPermission) Manifest.permission.CALL_PHONE else null,
                targetLabel = label
            )
        } else {
            DeviceActionOutcome(
                actionName = "makeCall",
                executed = false,
                status = DeviceActionStatus.FAILED,
                message = "Phone calling is not supported on this device.",
                targetLabel = label
            )
        }
    }

    fun callContact(contactName: String): DeviceActionOutcome {
        val cleanQuery = contactName.trim()
        if (cleanQuery.isEmpty()) {
            return DeviceActionOutcome(
                actionName = "callContact",
                executed = false,
                status = DeviceActionStatus.INVALID_INPUT,
                message = "Please specify a contact name to call."
            )
        }

        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasContactsPermission) {
            return DeviceActionOutcome(
                actionName = "callContact",
                executed = false,
                status = DeviceActionStatus.PERMISSION_REQUIRED,
                message = "Contacts permission is required before I can look up $cleanQuery.",
                missingPermission = Manifest.permission.READ_CONTACTS,
                targetLabel = cleanQuery
            )
        }

        val queried = queryContacts(cleanQuery)
        return when (val evaluated = DeviceActionValidator.evaluateContactMatches(cleanQuery, queried)) {
            ContactLookupResult.NotFound -> DeviceActionOutcome(
                actionName = "callContact",
                executed = false,
                status = DeviceActionStatus.NOT_FOUND,
                message = "I couldn't find any contact matching \"$cleanQuery\" in your contacts.",
                targetLabel = cleanQuery
            )
            is ContactLookupResult.Ambiguous -> DeviceActionOutcome(
                actionName = "callContact",
                executed = false,
                status = DeviceActionStatus.AMBIGUOUS,
                message = "I found ${evaluated.candidates.size} contacts matching \"$cleanQuery\": " +
                    evaluated.candidates.joinToString(", ") { it.displayName } +
                    ". Which one should I call?",
                candidates = evaluated.candidates,
                targetLabel = cleanQuery
            )
            is ContactLookupResult.ExactMatch -> {
                val callOutcome = makeCall(
                    phoneNumber = evaluated.candidate.phoneNumber,
                    displayLabel = evaluated.candidate.displayName
                )
                callOutcome.copy(actionName = "callContact")
            }
        }
    }

    private fun queryContacts(query: String): List<ContactCandidate> {
        val terms = DeviceActionValidator.expandContactQueryTerms(query)
        val results = mutableListOf<ContactCandidate>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    val num = cursor.getString(numIdx)?.trim().orEmpty()
                    if (name.isEmpty() || num.isEmpty()) continue
                    val lower = name.lowercase(Locale.US)
                    if (terms.any { term -> lower.contains(term) }) {
                        results.add(ContactCandidate(displayName = name, phoneNumber = num))
                    }
                }
            }
        }.onFailure { err ->
            Log.e(TAG, "Failed querying contacts", err)
        }
        return results
    }

    fun isDeviceOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun setFlashlight(enabled: Boolean): DeviceActionOutcome {
        val hasFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        if (!hasFlash) {
            return DeviceActionOutcome(
                actionName = "setFlashlight",
                executed = false,
                status = DeviceActionStatus.NOT_INSTALLED,
                message = "Flashlight hardware is not available on this device."
            )
        }
        val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
        return try {
            val backCameraId = camManager?.cameraIdList?.firstOrNull { id ->
                val chars = camManager.getCameraCharacteristics(id)
                chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (camManager != null && backCameraId != null) {
                camManager.setTorchMode(backCameraId, enabled)
                DeviceActionOutcome(
                    actionName = "setFlashlight",
                    executed = true,
                    status = DeviceActionStatus.SUCCESS,
                    message = if (enabled) "Flashlight turned ON." else "Flashlight turned OFF.",
                    targetLabel = "Flashlight"
                )
            } else {
                DeviceActionOutcome(
                    actionName = "setFlashlight",
                    executed = false,
                    status = DeviceActionStatus.FAILED,
                    message = "No camera flash unit was found."
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "setFlashlight error: ${e.message}")
            DeviceActionOutcome(
                actionName = "setFlashlight",
                executed = false,
                status = DeviceActionStatus.FAILED,
                message = "Could not toggle flashlight: ${e.localizedMessage ?: "busy camera"}"
            )
        }
    }

    fun openSystemPanel(panelType: String): DeviceActionOutcome {
        val type = panelType.trim().lowercase(Locale.US)
        val intent = when (type) {
            "wifi", "wi-fi" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            "internet", "mobile data" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            }
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "sound", "volume" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_VOLUME)
            } else {
                Intent(Settings.ACTION_SOUND_SETTINGS)
            }
            "display", "brightness" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "location", "gps" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (startActivitySafely(intent)) {
            DeviceActionOutcome(
                actionName = "openSystemPanel",
                executed = true,
                status = DeviceActionStatus.SUCCESS,
                message = "Opened ${type.replaceFirstChar { it.uppercase() }} settings panel.",
                targetLabel = type
            )
        } else {
            openApp("Settings")
        }
    }

    fun adjustVolume(mode: String): DeviceActionOutcome {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return DeviceActionOutcome(
                actionName = "adjustVolume",
                executed = false,
                status = DeviceActionStatus.FAILED,
                message = "Audio service unavailable."
            )
        return try {
            val clean = mode.trim().lowercase(Locale.US)
            when (clean) {
                "mute" -> {
                    audioManager.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.ADJUST_MUTE,
                        android.media.AudioManager.FLAG_SHOW_UI
                    )
                    DeviceActionOutcome("adjustVolume", true, DeviceActionStatus.SUCCESS, "Media volume muted.", targetLabel = "Mute")
                }
                "unmute" -> {
                    audioManager.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.ADJUST_UNMUTE,
                        android.media.AudioManager.FLAG_SHOW_UI
                    )
                    DeviceActionOutcome("adjustVolume", true, DeviceActionStatus.SUCCESS, "Media volume unmuted.", targetLabel = "Unmute")
                }
                "max" -> {
                    val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        max,
                        android.media.AudioManager.FLAG_SHOW_UI
                    )
                    DeviceActionOutcome("adjustVolume", true, DeviceActionStatus.SUCCESS, "Volume set to maximum.", targetLabel = "Max Volume")
                }
                "down" -> {
                    audioManager.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.ADJUST_LOWER,
                        android.media.AudioManager.FLAG_SHOW_UI
                    )
                    DeviceActionOutcome("adjustVolume", true, DeviceActionStatus.SUCCESS, "Turned volume down.", targetLabel = "Volume Down")
                }
                else -> {
                    audioManager.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.ADJUST_RAISE,
                        android.media.AudioManager.FLAG_SHOW_UI
                    )
                    DeviceActionOutcome("adjustVolume", true, DeviceActionStatus.SUCCESS, "Turned volume up.", targetLabel = "Volume Up")
                }
            }
        } catch (e: Exception) {
            DeviceActionOutcome(
                actionName = "adjustVolume",
                executed = false,
                status = DeviceActionStatus.FAILED,
                message = "Unable to adjust volume: ${e.localizedMessage ?: "restricted"}"
            )
        }
    }

    fun setAlarm(hour24: Int, minute: Int, label: String? = null): DeviceActionOutcome {
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour24.coerceIn(0, 23))
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label ?: "Zenvix Alarm")
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val formatted = String.format(Locale.US, "%02d:%02d", hour24.coerceIn(0, 23), minute.coerceIn(0, 59))
        return if (startActivitySafely(intent)) {
            DeviceActionOutcome(
                actionName = "setAlarm",
                executed = true,
                status = DeviceActionStatus.SUCCESS,
                message = "Opened Clock to set alarm for $formatted.",
                targetLabel = formatted
            )
        } else {
            DeviceActionOutcome(
                actionName = "setAlarm",
                executed = false,
                status = DeviceActionStatus.NOT_INSTALLED,
                message = "No Clock app was found to set an alarm."
            )
        }
    }

    fun setTimer(seconds: Int, label: String? = null): DeviceActionOutcome {
        val safeSec = seconds.coerceIn(1, 86_400)
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(android.provider.AlarmClock.EXTRA_LENGTH, safeSec)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label ?: "Zenvix Timer")
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (startActivitySafely(intent)) {
            DeviceActionOutcome(
                actionName = "setTimer",
                executed = true,
                status = DeviceActionStatus.SUCCESS,
                message = "Timer set for ${safeSec}s in Clock.",
                targetLabel = "${safeSec}s"
            )
        } else {
            DeviceActionOutcome(
                actionName = "setTimer",
                executed = false,
                status = DeviceActionStatus.NOT_INSTALLED,
                message = "No Clock app was found to start a timer."
            )
        }
    }

    fun getBatteryStatus(): DeviceActionOutcome {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val pct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val charging = bm?.isCharging == true
        return if (pct in 0..100) {
            val stateWord = if (charging) "charging" else "not charging"
            DeviceActionOutcome(
                actionName = "getBatteryStatus",
                executed = true,
                status = DeviceActionStatus.SUCCESS,
                message = "Battery is at $pct% and currently $stateWord.",
                targetLabel = "$pct%"
            )
        } else {
            DeviceActionOutcome(
                actionName = "getBatteryStatus",
                executed = false,
                status = DeviceActionStatus.FAILED,
                message = "Unable to read battery level on this device."
            )
        }
    }

    fun getCurrentDateTime(wantDate: Boolean): DeviceActionOutcome {
        val now = java.util.Date()
        val pattern = if (wantDate) "EEEE, dd MMM yyyy" else "hh:mm a"
        val formatted = java.text.SimpleDateFormat(pattern, Locale.getDefault()).format(now)
        val msg = if (wantDate) "Today's date is $formatted." else "The current time is $formatted."
        return DeviceActionOutcome(
            actionName = "getCurrentDateTime",
            executed = true,
            status = DeviceActionStatus.SUCCESS,
            message = msg,
            targetLabel = formatted
        )
    }

    fun executeOfflineSiriCommand(rawCommand: String): DeviceActionOutcome? {
        return when (val intent = OfflineSiriCommandParser.parse(rawCommand)) {
            null -> null
            OfflineSiriIntent.OpenWhatsApp -> openWhatsApp()
            is OfflineSiriIntent.OpenApp -> openApp(intent.appName)
            is OfflineSiriIntent.OpenUrl -> openUrl(intent.url)
            is OfflineSiriIntent.MakeCall -> makeCall(intent.phoneNumber)
            is OfflineSiriIntent.CallContact -> callContact(intent.contactName)
            is OfflineSiriIntent.SetFlashlight -> setFlashlight(intent.enabled)
            is OfflineSiriIntent.OpenSystemPanel -> openSystemPanel(intent.panelType)
            is OfflineSiriIntent.AdjustVolume -> adjustVolume(intent.mode)
            is OfflineSiriIntent.SetAlarm -> setAlarm(intent.hour24, intent.minute, intent.label)
            is OfflineSiriIntent.SetTimer -> setTimer(intent.seconds, intent.label)
            OfflineSiriIntent.CheckBattery -> getBatteryStatus()
            is OfflineSiriIntent.CheckDateTime -> getCurrentDateTime(intent.wantDate)
            OfflineSiriIntent.AskCreator -> DeviceActionOutcome(
                actionName = "creatorIdentity",
                executed = true,
                status = DeviceActionStatus.SUCCESS,
                message = "Mujhe Anugrah Das ne create kiya hai — Zenvix AI was created by Anugrah Das.",
                targetLabel = "Anugrah Das"
            )
        }
    }

    private fun startActivitySafely(intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not start intent ${intent.action}: ${e.message}")
            false
        }
    }

    private companion object {
        const val TAG = "DeviceActionBridge"
    }
}
